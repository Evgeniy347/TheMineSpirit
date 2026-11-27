package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.GraphBlock;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.graph.ChestReach;
import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphNames;
import com.theminespirit.core.board.graph.GraphState;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.intellect.StockCatalog;
import com.theminespirit.core.log.Perf;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.inventory.ChestWithdrawal;
import com.theminespirit.neoforge.terminal.CraftOrders;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

final class GraphInputWork {
    private enum Step {
        BUILDINGS,
        FACT_BASE,
        FACT_LISTS,
        FACT_CHESTS,
        FACT_FEEDBACK,
        FACT_WORLD,
        FACT_DIG,
        BLOCKS,
        MATERIALS,
        BASE_INPUTS,
        STORE,
        BASE,
        MORE3,
        MORE4,
        MORE5,
        MORE7,
        MORE6,
        DONE
    }

    private final ServerLevel level;
    private final BoardService service;
    private final List<SpiritEntity> spirits;
    private final StockView stock;
    private final FirstStepsRunner.StageScan scan;
    private final SupplyGraph graph;
    private final GraphSurroundings.Around around;
    private final Map<String, Long> phases = new LinkedHashMap<>();
    private Step step = Step.BUILDINGS;
    private int stage;
    private Optional<TownPlan> town = Optional.empty();
    private @Nullable BuildingStates buildings;
    private LifeInputs.@Nullable Input life;
    private @Nullable Set<String> events;
    private @Nullable GraphFacts facts;
    private @Nullable List<GraphBlock> blocks;
    private @Nullable Map<String, Map<String, Integer>> materials;
    private @Nullable Map<String, Map<String, Integer>> kits;
    private @Nullable Map<String, Integer> orders;
    private int storageFreePercent;
    private @Nullable GraphState state;

    GraphInputWork(
            ServerLevel level,
            BoardService service,
            List<SpiritEntity> spirits,
            StockView stock,
            FirstStepsRunner.StageScan scan,
            SupplyGraph graph,
            GraphSurroundings.Around around) {
        this.level = level;
        this.service = service;
        this.spirits = spirits;
        this.stock = stock;
        this.scan = scan;
        this.graph = graph;
        this.around = around;
    }

    @Nullable
    GraphState advance() {
        return switch (step) {
            case BUILDINGS -> {
                long started = System.nanoTime();
                stage = service.board().ledger().stage();
                buildings = GraphBuildInputs.of(level, service, scan, graph);
                town = SettlementPlotStore.get(level).town();
                life = LifeInputs.of(level, spirits, around.events());
                events = GraphInputs.events(
                        level, service, scan, stock, required(buildings, "BUILDINGS"), required(life, "BUILDINGS"));
                mark("buildings", started);
                step = Step.FACT_BASE;
                yield null;
            }
            case FACT_BASE -> {
                long started = System.nanoTime();
                facts = GraphFactInputs.of(
                        service, spirits, graph, required(buildings, "BUILDINGS"), scan, town, around);
                mark("factBase", started);
                step = Step.FACT_LISTS;
                yield null;
            }
            case FACT_LISTS -> {
                long started = System.nanoTime();
                LifeInputs.Input currentLife = required(life, "BUILDINGS");
                facts = required(facts, "FACT_BASE")
                        .withThreats(currentLife.threats())
                        .withPlayers(LifeInputs.players(level))
                        .withPlanBreaches(GraphFactInputs.planBreaches(level));
                mark("factLists", started);
                step = Step.FACT_CHESTS;
                yield null;
            }
            case FACT_CHESTS -> {
                long started = System.nanoTime();
                facts = required(facts, "FACT_BASE")
                        .withChests(GraphFactInputs.chestFacts(
                                StockReader.chests(level),
                                GraphFactInputs.reachAt(level),
                                ChestWithdrawal.remoteWithdrawal(level, spirits)));
                mark("factChests", started);
                step = Step.FACT_FEEDBACK;
                yield null;
            }
            case FACT_FEEDBACK -> {
                long started = System.nanoTime();
                facts = required(facts, "FACT_BASE")
                        .withFeedback(GraphWorldFeedbackInputs.of(level, spirits, scan.home(), service));
                mark("factFeedback", started);
                step = Step.FACT_WORLD;
                yield null;
            }
            case FACT_WORLD -> {
                long started = System.nanoTime();
                facts = required(facts, "FACT_BASE")
                        .withWorld(WorldFactsCache.facts(level, service, scan, town, required(buildings, "BUILDINGS")));
                mark("factWorld", started);
                step = Step.FACT_DIG;
                yield null;
            }
            case FACT_DIG -> {
                long started = System.nanoTime();
                facts = DigInputs.apply(level, spirits, required(facts, "FACT_BASE"));
                mark("factDig", started);
                GraphStockNote.log(required(facts, "FACT_BASE"), spirits);
                step = Step.BLOCKS;
                yield null;
            }
            case BLOCKS -> {
                long started = System.nanoTime();
                blocks = GraphInputs.blocks(service, graph);
                mark("blocks", started);
                step = Step.MATERIALS;
                yield null;
            }
            case MATERIALS -> {
                long started = System.nanoTime();
                materials = GraphInputs.materials(
                        level,
                        service,
                        stock,
                        scan,
                        graph,
                        required(buildings, "BUILDINGS"),
                        required(facts, "FACT_BASE"));
                mark("materials", started);
                step = Step.BASE_INPUTS;
                yield null;
            }
            case BASE_INPUTS -> {
                long started = System.nanoTime();
                kits = GraphInputs.kits(spirits, graph);
                orders = GraphNames.need(CraftOrders.shortage(level));
                storageFreePercent =
                        StoragePlanWatch.space(level, service.board().ledger()).freePercent();
                mark("baseInputs", started);
                step = Step.STORE;
                yield null;
            }
            case STORE -> {
                long started = System.nanoTime();
                facts = GraphStoreInputs.of(required(facts, "FACT_BASE"), level, graph);
                mark("factStore", started);
                step = Step.BASE;
                yield null;
            }
            case BASE -> {
                long started = System.nanoTime();
                state = new GraphState(
                                GraphNames.stock(
                                        ChestReach.reachableStock(
                                                stock.available(),
                                                required(facts, "FACT_BASE").chests(),
                                                service.supply()
                                                        .parts()
                                                        .carried()
                                                        .available()),
                                        StockCatalog.defaultTable().nutrition().keySet()),
                                stage,
                                required(kits, "BASE_INPUTS"),
                                required(buildings, "BUILDINGS"),
                                required(materials, "MATERIALS"),
                                storageFreePercent,
                                required(events, "BUILDINGS"),
                                around.world(),
                                around.distances(),
                                GraphFactInputs.stationCounts(graph, required(buildings, "BUILDINGS")))
                        .withOrders(required(orders, "BASE_INPUTS"))
                        .withFacts(required(facts, "FACT_BASE"), graph.costs().life());
                mark("base", started);
                step = Step.MORE3;
                yield null;
            }
            case MORE3 -> {
                long started = System.nanoTime();
                state = More3Inputs.apply(
                        More2Inputs.apply(
                                MoreInputs.apply(
                                        FarmInputs.apply(
                                                StoreInputs.apply(required(state, "BASE"), level, graph),
                                                level,
                                                graph,
                                                scan),
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
                step = Step.MORE4;
                yield null;
            }
            case MORE4 -> {
                long started = System.nanoTime();
                state = More4Inputs.apply(required(state, "MORE3"), level, spirits, scan, graph);
                mark("more4", started);
                step = Step.MORE5;
                yield null;
            }
            case MORE5 -> {
                long started = System.nanoTime();
                state = More5World.apply(required(state, "MORE4"), level, graph, scan, around);
                mark("more5", started);
                step = Step.MORE7;
                yield null;
            }
            case MORE7 -> {
                long started = System.nanoTime();
                state = More7Inputs.apply(required(state, "MORE5"), level, graph, scan);
                mark("more7", started);
                step = Step.MORE6;
                yield null;
            }
            case MORE6 -> {
                long started = System.nanoTime();
                state = More6Inputs.apply(required(state, "MORE7"), level, around, scan);
                mark("more6", started);
                step = Step.DONE;
                yield null;
            }
            case DONE -> required(state, "MORE6").withBlocks(required(blocks, "BLOCKS"));
        };
    }

    Map<String, Long> phases() {
        return Map.copyOf(phases);
    }

    private void mark(String name, long started) {
        long elapsed = System.nanoTime() - started;
        Perf.record("graph.in." + name, elapsed);
        phases.merge(name, elapsed / 1_000_000, Long::sum);
    }

    private static <T> T required(@Nullable T value, String step) {
        return Objects.requireNonNull(value, "Вход графа не собран: шаг " + step + " пропущен");
    }
}
