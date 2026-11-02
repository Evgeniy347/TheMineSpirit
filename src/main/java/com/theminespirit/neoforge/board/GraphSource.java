package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.graph.GraphNotes;
import com.theminespirit.core.board.graph.GraphPlan;
import com.theminespirit.core.board.graph.GraphPlanner;
import com.theminespirit.core.board.graph.GraphState;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.board.graph.StageBuilt;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.TownSnapshot;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.terminal.CraftOrders;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import org.slf4j.Logger;

public final class GraphSource {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double HOSTILE_RADIUS = 16;
    private static final long GRAPH_INTERVAL_TICKS = 600;
    private static final long DEEP_INTERVAL_TICKS = 1200;

    private final GraphNotes notes = new GraphNotes();
    private String lastBuildings = "";
    private long lastCycle = -1;
    private long lastDeep = -1;
    private Set<String> lastLive = Set.of();

    private record Half(
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
            boolean deep) {}

    private Half pending;
    private boolean surroundingsDone;

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
        if (lastCycle >= 0 && now >= lastCycle && now - lastCycle < GRAPH_INTERVAL_TICKS) {
            return live();
        }
        if (!surroundingsDone) {
            lastCycle = now;
            StageSurroundings.tick(level, service, now);
            surroundingsDone = true;
            return live();
        }
        SupplyGraph graph = DataTables.INSTANCE.graph().current();
        Optional<FirstStepsRunner.StageScan> scan = FirstStepsRunner.tick(level, service, spirits, stock, now);
        if (scan.isEmpty()) {
            lastLive = Set.of();
            surroundingsDone = false;
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
        pending = new Half(level, service, spirits, stock, stageScan, around, state, plan, graph, result, now, deep);
        surroundingsDone = false;
        return live();
    }

    private Set<String> finish(Half half) {
        GraphPost post = new GraphPost(
                half.level(),
                half.service(),
                half.spirits(),
                half.stock(),
                new GraphPost.Site(half.scan(), half.around()),
                half.graph(),
                half.now());
        List<String> unconnected = new ArrayList<>();
        Set<String> present = new HashSet<>();
        for (var existing : half.service().board().all()) {
            present.add(existing.id());
        }
        for (GraphTickets.Ticket ticket : half.result().post()) {
            if (present.contains(ticket.id())) {
                continue;
            }
            if (!post.post(ticket)) {
                unconnected.add(ticket.id());
            }
        }
        for (GraphPlan.Task task : half.plan().tasks()) {
            if (task.implemented() && task.priority() > 0) {
                GraphTicketIds.ids(task, half.graph())
                        .forEach(id -> half.service().board().reprioritize(id, task.priority()));
            }
        }
        report(half.plan(), half.result(), unconnected, half.now());
        if (half.deep()) {
            String buildings = new TreeMap<>(half.state().buildings()) + " "
                    + half.plan().tasks().stream()
                            .filter(task -> task.id().startsWith("building:"))
                            .map(task -> readiness(half.level(), half.service(), half.graph(), half.scan(), task))
                            .toList()
                    + " сундуков двора " + half.scan().facts().chests() + " у "
                    + half.scan().stage().workYard()
                    + " на доску "
                    + half.result().post().stream()
                            .map(GraphTickets.Ticket::id)
                            .filter(id -> id.contains("building:"))
                            .toList();
            if (!buildings.equals(lastBuildings)) {
                lastBuildings = buildings;
                LOGGER.info("[graph] постройки {}", buildings);
            }
        }
        tellStrategist(half.level(), half.graph(), half.state(), half.plan(), half.now());
        lastLive = half.result().live();
        pending = null;
        return live();
    }

    private Set<String> live() {
        Set<String> cached = lastLive;
        return cached == null ? Set.of() : cached;
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
        if (service.board().all().stream()
                .anyMatch(ticket -> ticket.boardCase() == BoardCase.REPAIR_BUILDING
                        && service.board()
                                .statusOf(ticket.id())
                                .filter(GraphSource::open)
                                .isPresent())) {
            events.add("building_damaged");
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

    private static String readiness(
            ServerLevel level,
            BoardService service,
            SupplyGraph graph,
            FirstStepsRunner.StageScan scan,
            GraphPlan.Task task) {
        SupplyGraph.Building building = graph.buildings().get(task.makes());
        if (building == null || !StageBuilt.ids().contains(task.makes())) {
            return task.makes() + "=" + task.priority() + (task.feasible() ? "" : "?");
        }
        TownSnapshot.Progress progress =
                FirstStepsRunner.progress(level, service, building.boardCase(), scan.stage(), scan.wood());
        int percent = progress.total() == 0 ? 0 : progress.done() * 100 / progress.total();
        return task.makes() + "=" + percent + "%(" + progress.done() + "/" + progress.total() + ")"
                + (task.feasible() ? "" : "?");
    }

    private static boolean open(BoardStatus status) {
        return status == BoardStatus.PENDING
                || status == BoardStatus.WAITING_RESOURCES
                || status == BoardStatus.ASSIGNED;
    }

    private void tellStrategist(ServerLevel level, SupplyGraph graph, GraphState state, GraphPlan plan, long now) {
        for (GraphPlan.Notice notice : plan.notices()) {
            if (!notes.due("notice:" + notice.building(), notice.reason(), now)) {
                continue;
            }
            String text = "[поселение] " + notice.reason();
            LOGGER.info("[graph] просьба стратегу: {}", text);
            if (current(graph, state, notice.building())) {
                for (ServerPlayer player : level.players()) {
                    player.sendSystemMessage(Component.literal(text));
                }
            }
        }
    }

    private static boolean current(SupplyGraph graph, GraphState state, String node) {
        SupplyGraph.Building building = graph.buildings().get(node);
        SupplyGraph.Item item = graph.items().get(node);
        int stage = building != null ? building.stage() : item != null ? item.stage() : 1;
        return stage <= state.stage();
    }

    private void report(GraphPlan plan, GraphTickets.Result result, List<String> unconnected, long now) {
        List<String> missing = result.unimplemented().stream()
                .filter(task -> task.priority() > 0)
                .map(GraphPlan.Task::id)
                .toList();
        if (!missing.isEmpty() && notes.due("unimplemented", now)) {
            LOGGER.info("[graph] нужны, но механик нет (implemented=false): {}", missing);
        }
        if (!unconnected.isEmpty() && notes.due("unconnected", now)) {
            LOGGER.info(
                    "[graph] задания графа без подключения к доске (их ставит свой сторож или нет цели): {}",
                    unconnected);
        }
        for (GraphPlan.Advice advice : plan.advice()) {
            if (notes.due("advice:" + advice.event() + ":" + advice.rule(), now)) {
                LOGGER.info("[graph] совет тактике ({}): {}", advice.event(), advice.rule());
            }
        }
    }
}
