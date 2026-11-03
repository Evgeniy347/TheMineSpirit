package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardLedger;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.MaterialSource;
import com.theminespirit.core.board.StageCatalog;
import com.theminespirit.core.board.StageRules;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.ToolFate;
import com.theminespirit.core.board.ToolsConfig;
import com.theminespirit.core.board.graph.GraphBuildingMaterials;
import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphKits;
import com.theminespirit.core.board.graph.GraphNames;
import com.theminespirit.core.board.graph.GraphState;
import com.theminespirit.core.board.graph.GraphState.Built;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.board.graph.StageBuilt;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.board.route.RouteMarker;
import com.theminespirit.core.board.route.RoutePlan;
import com.theminespirit.core.intellect.StockCatalog;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.repair.RepairCalculator;
import com.theminespirit.core.repair.RepairData;
import com.theminespirit.core.town.OrchardSites;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.terminal.CraftOrders;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

final class GraphInputs {
    private GraphInputs() {}

    static GraphState state(
            ServerLevel level,
            BoardService service,
            List<SpiritEntity> spirits,
            StockView stock,
            FirstStepsRunner.StageScan scan,
            SupplyGraph graph,
            GraphSurroundings.Around around) {
        int stage = service.board().ledger().stage();
        Map<String, Built> buildings = buildings(level, service, service.board().ledger(), scan, graph);
        Optional<TownPlan> town = SettlementPlotStore.get(level).town();
        LifeInputs.Input life = LifeInputs.of(level, spirits, around.events());
        GraphFacts facts = DigInputs.apply(
                level,
                spirits,
                GraphFactInputs.of(service, spirits, graph, buildings, scan, town, around)
                        .withThreats(life.threats())
                        .withChests(GraphFactInputs.chestFacts(StockReader.chests(level)))
                        .withWorld(WorldAreaInputs.of(
                                level,
                                service,
                                scan,
                                town,
                                BuildingAuditInputs.buildings(level, service, scan, buildings, town))));
        GraphState state = new GraphState(
                        GraphNames.stock(
                                stock.available(),
                                StockCatalog.defaultTable().nutrition().keySet()),
                        stage,
                        kits(spirits, graph),
                        buildings,
                        materials(level, service, stock, scan, graph, buildings, facts),
                        ChestSpaceWatch.space(level).freePercent(),
                        life.events(),
                        around.world(),
                        around.distances(),
                        GraphFactInputs.stationCounts(graph, buildings))
                .withOrders(GraphNames.need(CraftOrders.shortage(level)))
                .withFacts(GraphStoreInputs.of(facts, level, graph));
        GraphState more3 = More3Inputs.apply(
                More2Inputs.apply(
                        MoreInputs.apply(
                                FarmInputs.apply(StoreInputs.apply(state, level, graph), level, graph, scan),
                                level,
                                service,
                                spirits,
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
        GraphState more4 = More4Inputs.apply(more3, level, spirits, scan, graph);
        GraphState more5 = More5World.apply(more4, level, graph, scan, around);
        GraphState more7 = More7Inputs.apply(more5, level, graph, scan);
        return More6Inputs.apply(more7, level, around, scan);
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

    private static Map<String, Built> buildings(
            ServerLevel level,
            BoardService service,
            BoardLedger ledger,
            FirstStepsRunner.StageScan scan,
            SupplyGraph graph) {
        Map<String, Built> buildings = new HashMap<>(StageBuilt.of(scan.facts()));
        RoutePlan routes = RouteStore.get(level).plan();
        for (RouteMarker marker : routes.markers().values()) {
            buildings.put(marker.resource().pathBuilding(), marker.pathBuilt() ? Built.BUILT : Built.PLANNED);
            if (marker.routeBuilt()) {
                buildings.put(marker.resource().routeBuilding(), Built.BUILT);
            } else if (marker.pathBuilt()) {
                buildings.put(marker.resource().routeBuilding(), Built.PLANNED);
            }
        }
        Optional<TownPlan> town = SettlementPlotStore.get(level).town();
        buildings.putAll(planBuilt(level, service, scan, town));
        planStates(town, ledger, graph, buildings);
        Map<BoardCase, Integer> sharing = new EnumMap<>(BoardCase.class);
        graph.buildings().values().forEach(building -> sharing.merge(building.boardCase(), 1, Integer::sum));
        for (SupplyGraph.Building building : graph.buildings().values()) {
            if (buildings.containsKey(building.id())) {
                continue;
            }
            boolean ownCase = sharing.get(building.boardCase()) == 1;
            if (building.boardCase() == BoardCase.BUILD_PEN) {
                buildings.put(building.id(), PenBuild.built(service, building.id()) ? Built.BUILT : Built.PLANNED);
            } else if (ownCase && ledger.has(building.boardCase(), 1)) {
                buildings.put(building.id(), Built.BUILT);
            } else if (town.flatMap(plan -> OrchardSites.placement(plan, building.id()))
                    .isPresent()) {
                buildings.put(building.id(), orchardDone(service, building) ? Built.BUILT : Built.PLANNED);
            }
        }
        return buildings;
    }

    private static void planStates(
            Optional<TownPlan> town, BoardLedger ledger, SupplyGraph graph, Map<String, Built> buildings) {
        town.ifPresent(plan -> plan.placements()
                .forEach(place -> StageCatalog.graphId(place.id()).ifPresent(id -> {
                    SupplyGraph.Building building = graph.buildings().get(id);
                    if (building == null
                            || buildings.containsKey(id)
                            || !StageCatalog.watcherOwned(building.boardCase())) {
                        return;
                    }
                    boolean built = ledger.has(building.boardCase(), 1);
                    buildings.put(id, built ? Built.BUILT : Built.PLANNED);
                })));
    }

    private static Map<String, Built> planBuilt(
            ServerLevel level, BoardService service, FirstStepsRunner.StageScan scan, Optional<TownPlan> town) {
        BoardLedger ledger = service.board().ledger();
        Map<String, Built> states = new HashMap<>();
        StageRules rules = DataTables.INSTANCE.stages().current();
        int stage = service.board().ledger().stage();
        if (stage > rules.stages().size()) {
            return states;
        }
        for (String id : rules.stages().get(stage - 1).plan()) {
            Optional<StageCatalog.Entry> entry = StageCatalog.entry(id);
            if (entry.isEmpty() || entry.orElseThrow().boardCase() != BoardCase.BUILD_PLACED) {
                continue;
            }
            Optional<Boolean> world = town.isPresent()
                    ? worldBuilt(level, scan, town.orElseThrow(), entry.orElseThrow())
                    : Optional.empty();
            boolean built = ledger.hasPlan(id, 1) || world.orElse(false);
            if (world.orElse(false)) {
                ledger.notePlan(id, 1);
            }
            states.put(id, built ? Built.BUILT : Built.PLANNED);
        }
        return states;
    }

    private static Optional<Boolean> worldBuilt(
            ServerLevel level, FirstStepsRunner.StageScan scan, TownPlan town, StageCatalog.Entry entry) {
        Optional<BlockCoord> origin =
                StageCatalog.origin(entry.id(), town, scan.stage().anchor().y());
        if (origin.isEmpty()) {
            return Optional.of(false);
        }
        TicketTarget target =
                TicketTarget.at(origin.orElseThrow()).withScheme(entry.scheme()).withBuilding(entry.id());
        FirstStepsRunner.Placed placed =
                FirstStepsRunner.placedBlocks(level, TicketPlans.plan(BoardCase.BUILD_PLACED, 1, target), scan.wood());
        if (!placed.confident()) {
            return Optional.empty();
        }
        return Optional.of(placed.total() > 0 && placed.done() == placed.total());
    }

    private static boolean orchardDone(BoardService service, SupplyGraph.Building building) {
        return building.boardCase() == BoardCase.BUILD_ORCHARD
                && service.board()
                        .statusOf(GraphTickets.PREFIX + "building:" + building.id())
                        .filter(BoardStatus.DONE::equals)
                        .isPresent();
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
        GraphBuildingMaterials.of(facts, graph).forEach(materials::putIfAbsent);
        return materials;
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
