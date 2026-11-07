package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.graph.GraphNotes;
import com.theminespirit.core.board.graph.GraphPlan;
import com.theminespirit.core.board.graph.GraphState;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.board.graph.GraphWhy;
import com.theminespirit.core.board.graph.StageBuilt;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.town.TownSnapshot;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

final class GraphReporter {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final GraphNotes notes = new GraphNotes();
    private String lastBuildings = "";

    void world(ServerLevel level, GraphState state, long now) {
        GraphObserve.remember(state);
        if (notes.due("world", now)) {
            GraphObserve.world(level, state);
        }
    }

    void builds(
            ServerLevel level,
            BoardService service,
            SupplyGraph graph,
            GraphState state,
            FirstStepsRunner.StageScan scan,
            long now) {
        if (notes.due("plot:builds", now)) {
            GraphObserve.builds(level, service, graph, state, scan);
        }
    }

    void strategist(ServerLevel level, SupplyGraph graph, GraphState state, GraphPlan plan, long now) {
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

    void deep(GraphSource.Half half) {
        String buildings = buildingsText(half) + " "
                + half.plan().tasks().stream()
                        .filter(task -> task.id().startsWith("building:"))
                        .map(task ->
                                readiness(half.level(), half.service(), half.graph(), half.state(), half.scan(), task))
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

    void report(GraphPlan plan, GraphTickets.Result result, List<String> unconnected, boolean freeSpirit, long now) {
        List<String> missing = result.unimplemented().stream()
                .filter(task -> task.priority() > 0)
                .map(GraphPlan.Task::id)
                .toList();
        if (!missing.isEmpty() && notes.due("unimplemented", now)) {
            LOGGER.info("[graph] нужны, но механик нет (implemented=false): {}", missing);
        }
        if (!unconnected.isEmpty() && notes.due("unconnected", now)) {
            if (freeSpirit) {
                LOGGER.info(
                        "[graph] задания графа без подключения к доске (нет исполнителя или цели): {}", unconnected);
            } else {
                LOGGER.info("[graph] задания графа ждут свободного духа: {}", unconnected);
            }
        }
        for (GraphPlan.Advice advice : plan.advice()) {
            if (notes.due("advice:" + advice.event() + ":" + advice.rule(), now)) {
                LOGGER.info("[graph] совет тактике ({}): {}", advice.event(), advice.rule());
            }
        }
    }

    private static String readiness(
            ServerLevel level,
            BoardService service,
            SupplyGraph graph,
            GraphState state,
            FirstStepsRunner.StageScan scan,
            GraphPlan.Task task) {
        SupplyGraph.Building building = graph.buildings().get(task.makes());
        if (building == null || !StageBuilt.ids().contains(task.makes())) {
            return task.makes() + "=" + task.priority() + why(graph, state, task);
        }
        TownSnapshot.Progress progress =
                FirstStepsRunner.progress(level, service, building.boardCase(), scan.stage(), scan.wood());
        int percent = progress.total() == 0 ? 0 : progress.done() * 100 / progress.total();
        return task.makes() + "=" + percent + "%(" + progress.done() + "/" + progress.total() + ")"
                + why(graph, state, task);
    }

    private static String why(SupplyGraph graph, GraphState state, GraphPlan.Task task) {
        return task.feasible() ? "" : "?(" + GraphWhy.building(graph, state, task.makes()) + ")";
    }

    private static String buildingsText(GraphSource.Half half) {
        Map<String, String> shown = new TreeMap<>();
        half.state().buildings().forEach((id, state) -> shown.put(id, state.toString()));
        for (String id : List.of("mine", "wheat_field", "streets")) {
            GraphObserve.progressOf(half.level(), half.service(), half.graph(), half.state(), half.scan(), id)
                    .filter(progress -> progress.total() > 0)
                    .ifPresent(progress -> {
                        int percent = progress.done() * 100 / progress.total();
                        shown.computeIfPresent(
                                id,
                                (key, value) ->
                                        value + " " + percent + "%(" + progress.done() + "/" + progress.total() + ")");
                    });
        }
        return shown.toString();
    }

    private static boolean current(SupplyGraph graph, GraphState state, String node) {
        SupplyGraph.Building building = graph.buildings().get(node);
        SupplyGraph.Item item = graph.items().get(node);
        int stage = building != null ? building.stage() : item != null ? item.stage() : 1;
        return stage <= state.stage();
    }
}
