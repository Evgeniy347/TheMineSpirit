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
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.reload.DataTables;
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

    private final GraphNotes notes = new GraphNotes();
    private String lastBuildings = "";

    public Set<String> tick(
            ServerLevel level,
            BoardService service,
            List<SpiritEntity> spirits,
            StockView stock,
            Set<String> events,
            long now) {
        SupplyGraph graph = DataTables.INSTANCE.graph().current();
        Optional<FirstStepsRunner.StageScan> scan = FirstStepsRunner.tick(level, service, spirits, stock, now);
        if (scan.isEmpty()) {
            return Set.of();
        }
        RouteWatch.Result routes = RouteWatch.tick(level, scan.orElseThrow().home());
        GraphSurroundings.Around around = GraphSurroundings.of(
                        level, service, scan.orElseThrow().home())
                .with(events)
                .withRoutes(routes.events(), routes.world());
        GraphState state = GraphInputs.state(level, service, spirits, stock, scan.orElseThrow(), graph, around);
        GraphPlan plan = GraphPlanner.plan(graph, state);
        GraphTickets.Result result = GraphTickets.of(graph, state, plan);
        GraphPost post = new GraphPost(
                level, service, spirits, stock, new GraphPost.Site(scan.orElseThrow(), around), graph, now);
        List<String> unconnected = new ArrayList<>();
        for (GraphTickets.Ticket ticket : result.post()) {
            if (!post.post(ticket)) {
                unconnected.add(ticket.id());
            }
        }
        for (GraphPlan.Task task : plan.tasks()) {
            if (task.implemented() && task.priority() > 0) {
                GraphTicketIds.ids(task, graph).forEach(id -> service.board().reprioritize(id, task.priority()));
            }
        }
        report(plan, result, unconnected, now);
        String buildings = new TreeMap<>(state.buildings()) + " "
                + plan.tasks().stream()
                        .filter(task -> task.id().startsWith("building:"))
                        .map(task -> task.makes() + "=" + task.priority() + (task.feasible() ? "" : "?"))
                        .toList()
                + " сундуков двора " + scan.orElseThrow().facts().chests() + " у "
                + scan.orElseThrow().stage().workYard()
                + " на доску "
                + result.post().stream()
                        .map(GraphTickets.Ticket::id)
                        .filter(id -> id.contains("building:"))
                        .toList();
        if (!buildings.equals(lastBuildings)) {
            lastBuildings = buildings;
            LOGGER.info("[graph] постройки {}", buildings);
        }
        tellStrategist(level, graph, state, plan, now);
        return result.live();
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

    private static boolean open(BoardStatus status) {
        return status == BoardStatus.PENDING
                || status == BoardStatus.WAITING_RESOURCES
                || status == BoardStatus.ASSIGNED;
    }

    private void tellStrategist(ServerLevel level, SupplyGraph graph, GraphState state, GraphPlan plan, long now) {
        for (GraphPlan.Notice notice : plan.notices()) {
            if (!notes.due("notice:" + notice.building(), now)) {
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
