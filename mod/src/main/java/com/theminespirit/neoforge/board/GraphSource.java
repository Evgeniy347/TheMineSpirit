package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardActivities;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.VitalGate;
import com.theminespirit.core.board.graph.GraphCadence;
import com.theminespirit.core.board.graph.GraphDecisions;
import com.theminespirit.core.board.graph.GraphPlan;
import com.theminespirit.core.board.graph.GraphPlanner;
import com.theminespirit.core.board.graph.GraphState;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.board.graph.StageBuilt;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.terminal.CraftOrders;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Monster;
import org.slf4j.Logger;

public final class GraphSource {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double HOSTILE_RADIUS = 16;
    private static final long DEEP_INTERVAL_TICKS = 1200;

    final GraphDecisionNote decisionLog = new GraphDecisionNote();
    private final GraphReporter reporter = new GraphReporter();
    private final PetWork pets = new PetWork();
    private final GraphCadence cadence = new GraphCadence();

    private long lastDeep = -1;
    private Set<String> lastLive = Set.of();

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

    private Half pending;

    public Set<String> tick(
            ServerLevel level,
            BoardService service,
            List<SpiritEntity> spirits,
            StockView stock,
            Set<String> events,
            long now) {
        if (pending != null) {
            return finish(pending);
        }
        Set<String> signals = new HashSet<>(GraphSignals.of(events, spirits, service, now));
        signals.add(service.sources.rescueReach().readyEvent());
        if (!cadence.due(now, signals)) {
            return lastLive;
        }
        SupplyGraph graph = DataTables.INSTANCE.graph().current();
        Optional<FirstStepsRunner.StageScan> scan = FirstStepsRunner.tick(level, service, spirits, stock, now);
        if (scan.isEmpty()) {
            lastLive = Set.of();
            return lastLive;
        }
        FirstStepsRunner.StageScan stageScan = scan.orElseThrow();
        RouteWatch.Result routes = RouteWatch.tick(level, stageScan.home());
        GraphSurroundings.Around around = GraphSurroundings.of(level, service, stageScan.home())
                .with(events)
                .withRoutes(routes.events(), routes.world());
        GraphState state = GraphInputs.state(level, service, spirits, stock, stageScan, graph, around);
        boolean deep = lastDeep < 0 || now < lastDeep || now - lastDeep >= DEEP_INTERVAL_TICKS;
        if (deep) {
            lastDeep = now;
            settleBuilt(level, service, stock, stageScan, graph, state);
        }
        GraphPlan plan = GraphPlanner.plan(graph, state);
        GraphTickets.Result result = GraphTickets.of(graph, state, plan);
        pending = new Half(
                level, service, spirits, stock, stageScan, around, state, plan, graph, result, now, deep, signals);
        return lastLive;
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
            } else if (status != null && !activity) {
                continue;
            } else if (activity && closed(status)) {
                continue;
            }
            if (!post.post(ticket)) {
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
                                Monster.class, spirit.getBoundingBox().inflate(HOSTILE_RADIUS))
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

    private static void settleBuilt(
            ServerLevel level,
            BoardService service,
            StockView stock,
            FirstStepsRunner.StageScan scan,
            SupplyGraph graph,
            GraphState state) {
        for (SupplyGraph.Building building : graph.buildings().values()) {
            if (!StageBuilt.ids().contains(building.id()) || state.built(building.id()) == GraphState.Built.BUILT) {
                continue;
            }
            StageSettle.settle(level, service, stock, scan, building.boardCase(), level.getGameTime());
        }
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
                    .map(BoardTicket::id)
                    .filter(ticket -> !VitalGate.satisfies(ticket, needs))
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
