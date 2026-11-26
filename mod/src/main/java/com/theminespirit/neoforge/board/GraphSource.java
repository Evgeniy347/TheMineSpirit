package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardConfig;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.graph.GraphCadence;
import com.theminespirit.core.board.graph.GraphPlan;
import com.theminespirit.core.board.graph.GraphState;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.log.Perf;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.terminal.CraftOrders;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Monster;
import org.jspecify.annotations.Nullable;

public final class GraphSource {
    private static final long DEEP_INTERVAL_TICKS = 1200;

    final GraphDecisionNote decisionLog = new GraphDecisionNote();
    private final GraphReporter reporter = new GraphReporter();
    private final PetWork pets = new PetWork();
    private final GraphCadence cadence = new GraphCadence();
    private final QuarryWatch quarries = new QuarryWatch();

    private long lastDeep = -1;
    private Set<String> lastLive = Set.of();
    private final Map<String, Long> phases = new LinkedHashMap<>();

    record Half(
            ServerLevel level,
            BoardService service,
            List<SpiritEntity> spirits,
            StockView stock,
            FirstStepsRunner.StageScan scan,
            GraphSurroundings.Around around,
            GraphState state,
            GraphPlan plan,
            SupplyGraph graph,
            GraphTickets.Result result,
            long now,
            boolean deep,
            Set<String> signals) {}

    private GraphPreparation.@Nullable Work preparation;
    private @Nullable GraphPosting posting;
    private @Nullable GraphReport reporting;

    public Set<String> tick(
            ServerLevel level,
            BoardService service,
            List<SpiritEntity> spirits,
            StockView stock,
            Set<String> events,
            long now) {
        phases.clear();
        if (posting != null) {
            long started = System.nanoTime();
            if (posting.advance()) {
                finishPosting(posting);
            }
            mark("finish", started);
            return lastLive;
        }
        long started = System.nanoTime();
        Set<String> signals = new HashSet<>(GraphSignals.of(events, spirits, service, now));
        signals.add(service.sources.rescueReach().readyEvent());
        mark("signals", started);
        if (!cadence.due(now, signals)) {
            return lastLive;
        }
        SupplyGraph graph = DataTables.INSTANCE.graph().current();
        boolean deep = lastDeep < 0 || now < lastDeep || now - lastDeep >= DEEP_INTERVAL_TICKS;
        if (deep) {
            lastDeep = now;
        }
        preparation = GraphPreparation.start(
                level, service, spirits, stock, quarries, graph, now, deep, events, signals, phases);
        return lastLive;
    }

    public void advance() {
        if (reporting != null) {
            reporting.advance(reporter, decisionLog);
            if (reporting.done()) {
                reporting = null;
            }
        }
        GraphPreparation.Work work = preparation;
        if (work == null) {
            return;
        }
        try {
            Half half = work.advance();
            phases.putAll(work.phases());
            if (work.empty()) {
                preparation = null;
                lastLive = Set.of();
            } else if (half != null) {
                posting = new GraphPosting(half, pets);
                preparation = null;
                lastLive = new HashSet<>(half.result().live());
            }
        } catch (RuntimeException error) {
            preparation = null;
            posting = null;
            throw error;
        }
    }

    public Map<String, Long> lastPhases() {
        return Map.copyOf(phases);
    }

    private void mark(String name, long started) {
        long elapsed = System.nanoTime() - started;
        Perf.record("graph.src." + name, elapsed);
        phases.merge(name, elapsed / 1_000_000, Long::sum);
    }

    private void finishPosting(GraphPosting work) {
        GraphSource.Half half = work.half();
        cadence.finished(half.now(), half.signals());
        lastLive = work.post().live(half.result().live());
        posting = null;
        reporting = new GraphReport(half, work.unconnected());
    }

    public static Set<String> events(
            ServerLevel level,
            BoardService service,
            List<SpiritEntity> spirits,
            Set<String> craters,
            Set<String> fences) {
        Set<String> events = new HashSet<>();
        if (spirits.stream()
                .anyMatch(spirit -> !level.getEntitiesOfClass(
                                Monster.class,
                                spirit.getBoundingBox()
                                        .inflate(BoardConfig.current().facts().hostileScanRadius()))
                        .isEmpty())) {
            events.add("hostile_nearby");
        }
        if (!craters.isEmpty()) {
            events.add("creeper_crater");
        }
        if (!fences.isEmpty()) {
            events.add("fence_broken");
        }
        if (level.isNight()) {
            events.add("night");
        }
        if (GraphFood.hungry(spirits)) {
            events.add("hungry");
        }
        if (CraftOrders.waiting(level)) {
            events.add(CraftOrders.EVENT);
        }
        return Set.copyOf(events);
    }

    static boolean closed(BoardStatus status) {
        return status == BoardStatus.DONE || status == BoardStatus.FAILED || status == BoardStatus.CANCELLED;
    }
}
