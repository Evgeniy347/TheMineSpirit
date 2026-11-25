package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardActivities;
import com.theminespirit.core.board.BoardConfig;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.VitalGate;
import com.theminespirit.core.board.graph.GraphCadence;
import com.theminespirit.core.board.graph.GraphDecisions;
import com.theminespirit.core.board.graph.GraphPlan;
import com.theminespirit.core.board.graph.GraphState;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.log.Perf;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.terminal.CraftOrders;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Monster;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class GraphSource {
    private static final Logger LOGGER = LogUtils.getLogger();
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

    private GraphSource.@Nullable Half pending;
    private GraphPreparation.@Nullable Work preparation;

    public Set<String> tick(
            ServerLevel level,
            BoardService service,
            List<SpiritEntity> spirits,
            StockView stock,
            Set<String> events,
            long now) {
        phases.clear();
        if (pending != null) {
            long started = System.nanoTime();
            Set<String> done = finish(pending);
            mark("finish", started);
            return done;
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
        if (preparation == null) {
            lastLive = Set.of();
        }
        return lastLive;
    }

    public void advance() {
        GraphPreparation.Work work = preparation;
        if (work == null) {
            return;
        }
        try {
            Half half = work.advance();
            phases.putAll(work.phases());
            if (half != null) {
                pending = half;
                preparation = null;
            }
        } catch (RuntimeException error) {
            preparation = null;
            pending = null;
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

    private Set<String> finish(Half half) {
        pending = null;
        GraphPost post = new GraphPost(
                half.level(),
                half.service(),
                half.spirits(),
                half.stock(),
                new GraphPost.Site(half.scan(), half.around(), pets, half.state()),
                half.graph(),
                half.now());
        List<String> unconnected = new ArrayList<>();
        GraphStanding.seed(post, half.plan().tasks(), half.result().live());
        for (GraphTickets.Ticket ticket : half.result().post()) {
            BoardStatus status = half.service().board().statusOf(ticket.id()).orElse(null);
            boolean activity = BoardActivities.isActivity(ticket.task().boardCase());
            if (status == BoardStatus.DONE) {
                if (activity) {
                    continue;
                }
                half.service().board().reopen(ticket.id());
            } else if (status == BoardStatus.BLOCKED) {
                if (!half.service().board().failures().unblock(ticket.id(), half.now())) {
                    continue;
                }
            } else if (status != null && !activity) {
                continue;
            } else if (activity && closed(status)) {
                continue;
            }
            if (!TicketIsolation.post(ticket.id(), () -> post.post(ticket))) {
                if (!post.waiting().contains(ticket.id())) {
                    unconnected.add(ticket.id());
                }
            }
        }
        post.flush();
        StageWorldGate.apply(
                half.level(),
                half.service(),
                half.scan().stage(),
                half.scan().wood(),
                half.state().facts());
        for (GraphPlan.Task task : half.plan().tasks()) {
            if (task.implemented() && task.priority() > 0) {
                for (String id : GraphTicketIds.ids(task, half.graph())) {
                    try {
                        half.service().board().reprioritize(id, task.priority());
                    } catch (RuntimeException error) {
                        LOGGER.error(
                                "[incident] граф не смог обновить приоритет задания {} до {}: {}; "
                                        + "остальные задания и тик сервера идут",
                                id,
                                task.priority(),
                                String.valueOf(error.getMessage()),
                                error);
                    }
                }
            }
        }
        reporter.report(half.plan(), half.result(), unconnected, freeSpirit(half), half.now());
        decisionLog.note(half.plan(), half.now(), vitals(half));
        decisionLog.noteFailures(half.state(), half.now());
        reporter.world(half.level(), half.state(), half.now());
        reporter.builds(half.level(), half.service(), half.graph(), half.state(), half.scan(), half.now());
        if (half.deep()) {
            reporter.deep(half);
        }
        reporter.strategist(half.level(), half.graph(), half.state(), half.plan(), half.now());
        cadence.finished(half.now(), half.signals());
        lastLive = post.live(half.result().live());
        return lastLive;
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

    private static List<GraphDecisions.Vital> vitals(Half half) {
        List<GraphDecisions.Vital> rows = new ArrayList<>();
        for (SpiritEntity spirit : half.spirits()) {
            if (spirit.isRemoved()) {
                continue;
            }
            Map<String, String> needs = SpiritVitals.needs(spirit);
            if (needs.isEmpty()) {
                continue;
            }
            String id = spirit.getUUID().toString();
            String doing = half.service().board().assignedTo(spirit.getUUID()).stream()
                    .filter(ticket -> VitalGate.blocksWork(ticket, needs))
                    .map(BoardTicket::id)
                    .findFirst()
                    .orElse("");
            String task = half.plan().tasks().stream()
                    .filter(candidate -> candidate.spirit().filter(id::equals).isPresent())
                    .map(candidate -> GraphTickets.PREFIX + candidate.id())
                    .filter(ticket -> VitalGate.satisfies(ticket, needs))
                    .findFirst()
                    .orElse("");
            needs.forEach((need, reason) -> rows.add(new GraphDecisions.Vital(id, need, reason, task, doing)));
        }
        return List.copyOf(rows);
    }

    private static boolean closed(BoardStatus status) {
        return status == BoardStatus.DONE || status == BoardStatus.FAILED || status == BoardStatus.CANCELLED;
    }

    private static boolean freeSpirit(Half half) {
        return half.spirits().stream()
                .anyMatch(spirit -> !spirit.isRemoved()
                        && half.service().board().assignedTo(spirit.getUUID()).isEmpty()
                        && half.service().board().occupation(spirit.getUUID()).isEmpty());
    }
}
