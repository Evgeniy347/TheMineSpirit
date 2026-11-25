package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.ExpeditionRule;
import com.theminespirit.core.board.GraphBlock;
import com.theminespirit.core.board.MaterialSource;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.board.api.ToolFate;
import com.theminespirit.core.board.api.ToolsConfig;
import com.theminespirit.core.board.graph.ChestReach;
import com.theminespirit.core.board.graph.GraphBuildingMaterials;
import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphKits;
import com.theminespirit.core.board.graph.GraphNames;
import com.theminespirit.core.board.graph.GraphState;
import com.theminespirit.core.board.graph.GraphState.Built;
import com.theminespirit.core.board.graph.StageBuilt;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.intellect.StockCatalog;
import com.theminespirit.core.log.Perf;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.repair.RepairCalculator;
import com.theminespirit.core.repair.RepairData;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.terminal.CraftOrders;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

final class GraphInputs {
    private static final Map<String, Long> PHASES = new LinkedHashMap<>();

    private GraphInputs() {}

    static Map<String, Long> lastPhases() {
        return Map.copyOf(PHASES);
    }

    static void mark(String name, long started) {
        long elapsed = System.nanoTime() - started;
        Perf.record("graph.in." + name, elapsed);
        PHASES.merge(name, elapsed / 1_000_000, Long::sum);
    }

    static GraphState state(
            ServerLevel level,
            BoardService service,
            List<SpiritEntity> spirits,
            StockView stock,
            FirstStepsRunner.StageScan scan,
            SupplyGraph graph,
            GraphSurroundings.Around around) {
        PHASES.clear();
        int stage = service.board().ledger().stage();
        long started = System.nanoTime();
        BuildingStates buildings = GraphBuildInputs.of(level, service, scan, graph);
        mark("buildings", started);
        Optional<TownPlan> town = SettlementPlotStore.get(level).town();
        LifeInputs.Input life = LifeInputs.of(level, spirits, around.events());
        Set<String> events = events(level, service, scan, stock, buildings, life);
        started = System.nanoTime();
        GraphFacts baseFacts = GraphFactInputs.of(service, spirits, graph, buildings, scan, town, around);
        mark("factBase", started);
        started = System.nanoTime();
        GraphFacts listed = baseFacts
                .withThreats(life.threats())
                .withPlayers(LifeInputs.players(level))
                .withPlanBreaches(GraphFactInputs.planBreaches(level));
        mark("factLists", started);
        started = System.nanoTime();
        GraphFacts chests = listed.withChests(
                GraphFactInputs.chestFacts(StockReader.chests(level), GraphFactInputs.reachAt(level)));
        mark("factChests", started);
        started = System.nanoTime();
        GraphFacts feedback = chests.withFeedback(GraphWorldFeedbackInputs.of(level, spirits, scan.home(), service));
        mark("factFeedback", started);
        started = System.nanoTime();
        GraphFacts world = feedback.withWorld(WorldFactsCache.facts(level, service, scan, town, buildings));
        mark("factWorld", started);
        started = System.nanoTime();
        GraphFacts facts = DigInputs.apply(level, spirits, world);
        mark("factDig", started);
        GraphStockNote.log(facts, spirits);
        List<GraphBlock> blocks = blocks(service, graph);
        started = System.nanoTime();
        GraphState state = new GraphState(
                        GraphNames.stock(
                                ChestReach.reachableStock(stock.available(), facts.chests()),
                                StockCatalog.defaultTable().nutrition().keySet()),
                        stage,
                        kits(spirits, graph),
                        buildings,
                        materials(level, service, stock, scan, graph, buildings, facts),
                        StoragePlanWatch.space(level).freePercent(),
                        events,
                        around.world(),
                        around.distances(),
                        GraphFactInputs.stationCounts(graph, buildings))
                .withOrders(GraphNames.need(CraftOrders.shortage(level)))
                .withFacts(
                        GraphStoreInputs.of(facts, level, graph), graph.costs().life());
        mark("base", started);
        return layers(state, level, service, spirits, scan, graph, around).withBlocks(blocks);
    }

    private static Set<String> events(
            ServerLevel level,
            BoardService service,
            FirstStepsRunner.StageScan scan,
            StockView stock,
            Map<String, Built> buildings,
            LifeInputs.Input life) {
        Set<String> events = new LinkedHashSet<>(life.events());
        if (ExpeditionRule.ready(buildings.get("mine"), ExpeditionGate.ready(stock, level))) {
            events.add("expedition_ready");
        }
        SettlementEvents.add(events, level, service, scan);
        return events;
    }

    private static List<GraphBlock> blocks(BoardService service, SupplyGraph graph) {
        List<GraphBlock> blocks = new ArrayList<>(service.board().failures().graphBlocks());
        blocks.addAll(service.supply().shortages());
        blocks.addAll(service.sources
                .graphFailures()
                .pathBlocks(service.board().now(), graph.costs().choice().failureHalfLife(), (int)
                        Math.ceil(graph.costs().choice().failureTargets())));
        return blocks;
    }

    private static GraphState layers(
            GraphState state,
            ServerLevel level,
            BoardService service,
            List<SpiritEntity> spirits,
            FirstStepsRunner.StageScan scan,
            SupplyGraph graph,
            GraphSurroundings.Around around) {
        long started = System.nanoTime();
        GraphState more3 = More3Inputs.apply(
                More2Inputs.apply(
                        MoreInputs.apply(
                                FarmInputs.apply(StoreInputs.apply(state, level, graph), level, graph, scan),
                                level,
                                service,
                                scan,
                                graph),
                        level,
                        scan),
                level,
                service,
                spirits,
                graph,
                around,
                scan);
        mark("more3", started);
        started = System.nanoTime();
        GraphState more4 = More4Inputs.apply(more3, level, spirits, scan, graph);
        mark("more4", started);
        started = System.nanoTime();
        GraphState more5 = More5World.apply(more4, level, graph, scan, around);
        mark("more5", started);
        started = System.nanoTime();
        GraphState more7 = More7Inputs.apply(more5, level, graph, scan);
        mark("more7", started);
        started = System.nanoTime();
        GraphState done = More6Inputs.apply(more7, level, around, scan);
        mark("more6", started);
        return done;
    }

    static Map<String, Map<String, Integer>> kits(List<SpiritEntity> spirits, SupplyGraph graph) {
        ToolsConfig config = DataTables.INSTANCE.board().current().tools();
        int percent = RepairData.loadDefault().maxDurabilityPercent();
        Map<String, Map<String, Integer>> kits = new HashMap<>();
        for (SpiritEntity spirit : spirits) {
            List<String> items = new ArrayList<>();
            for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
                kept(spirit.getInventory().getItem(slot), config, percent).ifPresent(items::add);
            }
            if (spirit.mainHandOwned()) {
                kept(spirit.getMainHandItem(), config, percent).ifPresent(items::add);
            }
            kits.put(spirit.getUUID().toString(), GraphKits.of(items, graph));
        }
        return kits;
    }

    private static Optional<String> kept(ItemStack stack, ToolsConfig config, int percent) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (stack.getMaxDamage() > 0 && stack.getDamageValue() > 0) {
            int durability = stack.getMaxDamage() - stack.getDamageValue();
            boolean damaged = RepairCalculator.damagedEnoughToRepair(durability, stack.getMaxDamage(), percent);
            if (config.fate(itemId, damaged) == ToolFate.REPLACE) {
                return Optional.empty();
            }
        }
        return Optional.of(itemId);
    }

    private static Map<String, Map<String, Integer>> materials(
            ServerLevel level,
            BoardService service,
            StockView stock,
            FirstStepsRunner.StageScan scan,
            SupplyGraph graph,
            Map<String, Built> buildings,
            GraphFacts facts) {
        MaterialSource source = FirstStepsRunner.materialSource(level);
        int stage = service.board().ledger().stage();
        Map<String, Map<String, Integer>> materials = new HashMap<>();
        for (String id : StageBuilt.ids()) {
            SupplyGraph.Building building = graph.buildings().get(id);
            if (building == null || building.stage() > stage || buildings.get(id) == Built.BUILT) {
                continue;
            }
            if (building.boardCase() == BoardCase.CLEAR_GROUNDS || booked(service, building.boardCase())) {
                materials.put(id, Map.of());
                continue;
            }
            Map<String, Integer> need = FirstStepsRunner.materialsOf(level, stock, scan, building.boardCase());
            materials.put(id, known(graph, source, need));
        }
        return GraphBuildingMaterials.merged(materials, facts, graph);
    }

    private static boolean booked(BoardService service, BoardCase boardCase) {
        return FirstStepsRunner.ticketIds(boardCase).stream()
                .anyMatch(id -> service.board()
                        .statusOf(id)
                        .filter(status -> status == BoardStatus.PENDING
                                || status == BoardStatus.WAITING_RESOURCES
                                || status == BoardStatus.ASSIGNED)
                        .isPresent());
    }

    private static Map<String, Integer> known(SupplyGraph graph, MaterialSource source, Map<String, Integer> need) {
        Map<String, Integer> raw = new HashMap<>();
        need.forEach((item, count) -> {
            if (graph.items().containsKey(GraphNames.graphItem(item))) {
                raw.merge(item, count, Integer::sum);
            } else {
                FirstStepsRunner.expand(source, item, count, raw, 0);
            }
        });
        Map<String, Integer> known = new HashMap<>();
        GraphNames.need(raw).forEach((item, count) -> {
            if (graph.items().containsKey(item)) {
                known.put(item, count);
            }
        });
        return known;
    }
}
