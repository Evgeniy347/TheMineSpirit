package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.graph.GraphPlan;
import com.theminespirit.core.board.graph.GraphPlanner;
import com.theminespirit.core.board.graph.GraphState;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.board.graph.StageBuilt;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.log.Perf;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

final class GraphPreparation {
    enum Phase {
        STAGE,
        QUARRY,
        AROUND,
        STATE,
        BUILT,
        PLAN,
        TICKETS,
        DONE
    }

    private GraphPreparation() {}

    static GraphPreparation.Work start(
            ServerLevel level,
            BoardService service,
            List<SpiritEntity> spirits,
            StockView stock,
            QuarryWatch quarryWatch,
            SupplyGraph graph,
            long now,
            boolean deep,
            Set<String> events,
            Set<String> signals,
            Map<String, Long> phases) {
        return new Work(level, service, spirits, stock, quarryWatch, graph, now, deep, events, signals, phases);
    }

    static final class Work {
        private final ServerLevel level;
        private final BoardService service;
        private final List<SpiritEntity> spirits;
        private final StockView stock;
        private final QuarryWatch quarryWatch;
        private Optional<FirstStepsRunner.StageScan> scan = Optional.empty();
        private final SupplyGraph graph;
        private final long now;
        private final boolean deep;
        private final Set<String> events;
        private final Set<String> signals;
        private final Map<String, Long> phases;
        private Phase phase = Phase.STAGE;
        private boolean empty;
        private QuarryWatch.@Nullable Result quarries;
        private GraphSurroundings.@Nullable Around around;
        private @Nullable GraphInputWork inputs;
        private @Nullable GraphState state;
        private @Nullable List<SupplyGraph.Building> builtWork;
        private int builtCursor;
        private @Nullable GraphPlan plan;
        private GraphTickets.@Nullable Result result;

        private Work(
                ServerLevel level,
                BoardService service,
                List<SpiritEntity> spirits,
                StockView stock,
                QuarryWatch quarryWatch,
                SupplyGraph graph,
                long now,
                boolean deep,
                Set<String> events,
                Set<String> signals,
                Map<String, Long> phases) {
            this.level = level;
            this.service = service;
            this.spirits = spirits;
            this.stock = stock;
            this.quarryWatch = quarryWatch;
            this.graph = graph;
            this.now = now;
            this.deep = deep;
            this.events = events;
            this.signals = signals;
            this.phases = phases;
        }

        GraphSource.@Nullable Half advance() {
            return switch (phase) {
                case STAGE -> {
                    long started = System.nanoTime();
                    Optional<FirstStepsRunner.StageScan> found =
                            FirstStepsRunner.tick(level, service, spirits, stock, now);
                    mark(phases, "stage", started);
                    if (found.isPresent()) {
                        scan = found;
                        phase = Phase.QUARRY;
                    } else {
                        empty = true;
                        phase = Phase.DONE;
                    }
                    yield null;
                }
                case QUARRY -> {
                    long started = System.nanoTime();
                    quarries = quarryWatch.tick(level, requireScan().home(), now);
                    mark(phases, "quarry", started);
                    phase = Phase.AROUND;
                    yield null;
                }
                case AROUND -> {
                    QuarryWatch.Result found = requireQuarries();
                    FirstStepsRunner.StageScan current = requireScan();
                    long started = System.nanoTime();
                    around = GraphSurroundings.of(
                                    level,
                                    service,
                                    current.home(),
                                    found.nearest(),
                                    found.distances(),
                                    TreeCenterLogs.centers(current.stage()),
                                    cell -> current.stage().orchardTerritory(cell.x(), cell.z()))
                            .with(events)
                            .withRoutes(found.events(), found.world());
                    mark(phases, "around", started);
                    phase = Phase.STATE;
                    yield null;
                }
                case STATE -> {
                    GraphSurroundings.Around surroundings = requireAround();
                    long started = System.nanoTime();
                    GraphInputWork current = inputs;
                    if (current == null) {
                        current = GraphInputs.work(level, service, spirits, stock, requireScan(), graph, surroundings);
                        inputs = current;
                    }
                    GraphState collected = current.advance();
                    if (collected != null) {
                        state = collected;
                        current.phases().forEach((name, millis) -> phases.merge("in." + name, millis, Long::sum));
                        inputs = null;
                        phase = Phase.BUILT;
                    }
                    mark(phases, "state", started);
                    yield null;
                }
                case BUILT -> {
                    GraphState facts = requireState();
                    if (deep) {
                        long started = System.nanoTime();
                        if (builtWork == null) {
                            builtWork = graph.buildings().values().stream()
                                    .filter(building -> building.boardCase() != null
                                            && StageBuilt.ids().contains(building.id())
                                            && facts.built(building.id()) != GraphState.Built.BUILT)
                                    .toList();
                            builtCursor = 0;
                        }
                        int end = Math.min(builtWork.size(), builtCursor + 4);
                        while (builtCursor < end) {
                            SupplyGraph.Building building = builtWork.get(builtCursor++);
                            StageSettle.settle(
                                    level, service, stock, requireScan(), building.boardCase(), level.getGameTime());
                        }
                        mark(phases, "built", started);
                        if (builtCursor >= builtWork.size()) {
                            builtWork = null;
                            phase = Phase.PLAN;
                        }
                    } else {
                        phase = Phase.PLAN;
                    }
                    yield null;
                }
                case PLAN -> {
                    GraphState facts = requireState();
                    long started = System.nanoTime();
                    plan = GraphPlanner.plan(graph, facts);
                    mark(phases, "plan", started);
                    phase = Phase.TICKETS;
                    yield null;
                }
                case TICKETS -> {
                    GraphState facts = requireState();
                    GraphPlan planned = requirePlan();
                    long started = System.nanoTime();
                    result = GraphTickets.of(graph, facts, planned);
                    mark(phases, "tickets", started);
                    phase = Phase.DONE;
                    yield null;
                }
                case DONE -> half();
            };
        }

        Map<String, Long> phases() {
            return Map.copyOf(phases);
        }

        boolean empty() {
            return empty;
        }

        private FirstStepsRunner.StageScan requireScan() {
            return scan.orElseThrow(() -> new IllegalStateException("Скан этапа не выполнен: фаза STAGE пропущена"));
        }

        private GraphSource.Half half() {
            return new GraphSource.Half(
                    level,
                    service,
                    spirits,
                    stock,
                    requireScan(),
                    Objects.requireNonNull(around, "Окружение графа не посчитано: фаза AROUND пропущена"),
                    Objects.requireNonNull(state, "Вход графа не собран: фаза STATE пропущена"),
                    Objects.requireNonNull(plan, "План графа не построен: фаза PLAN пропущена"),
                    graph,
                    Objects.requireNonNull(result, "Билеты графа не собраны: фаза TICKETS пропущена"),
                    now,
                    deep,
                    signals);
        }

        private QuarryWatch.Result requireQuarries() {
            return Objects.requireNonNull(quarries, "Карьеры не измерены: фаза QUARRY пропущена");
        }

        private GraphSurroundings.Around requireAround() {
            return Objects.requireNonNull(around, "Окружение графа не посчитано: фаза AROUND пропущена");
        }

        private GraphState requireState() {
            return Objects.requireNonNull(state, "Вход графа не собран: фаза STATE пропущена");
        }

        private GraphPlan requirePlan() {
            return Objects.requireNonNull(plan, "План графа не построен: фаза PLAN пропущена");
        }
    }

    private static void mark(Map<String, Long> phases, String name, long started) {
        long elapsed = System.nanoTime() - started;
        Perf.record("graph.src." + name, elapsed);
        phases.merge(name, elapsed / 1_000_000, Long::sum);
    }
}
