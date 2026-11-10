package com.theminespirit.core.board.graph;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphState.Built;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class GraphCaseFixtures {
    private GraphCaseFixtures() {}

    static GraphTickets.Result tickets(GraphState state) {
        return GraphTickets.of(GraphWorld.GRAPH, state, GraphPlanner.plan(GraphWorld.GRAPH, state));
    }

    static GraphWorld busySettlement() {
        GraphWorld world =
                GraphWorld.settled().with("oak_log", 64).with("oak_planks", 64).with("food", 8);
        world.buildings.put("mine", Built.PLANNED);
        world.materials.put("mine", Map.of("torch", 6));
        return world;
    }

    static int maxWork(GraphPlan plan) {
        return plan.tasks().stream()
                .filter(task -> !task.id().startsWith("event:"))
                .mapToInt(GraphPlan.Task::priority)
                .max()
                .orElse(0);
    }

    static GraphFacts facts(GraphSiteFacts.Terrain terrain) {
        return new GraphFacts(
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Map.of(),
                List.of(),
                terrain,
                new GraphSiteFacts.StageReqs(Map.of(), Map.of()),
                GraphFeedback.none(),
                List.of(),
                List.of(),
                List.of());
    }

    static List<String> ids(GraphPlan plan) {
        return plan.tasks().stream().map(GraphPlan.Task::id).toList();
    }

    static BlockCoord home() {
        return new BlockCoord(0, 64, 0);
    }

    static List<ScriptCall> calls(TaskNode node) {
        List<ScriptCall> found = new ArrayList<>();
        collect(node, found);
        return found;
    }

    static TaskNode materialPlan(String item, int count) {
        return TicketPlans.plan(
                BoardCase.GATHER_MATERIALS, 1, TicketTarget.at(home()).withSupply(item, count));
    }

    private static void collect(TaskNode node, List<ScriptCall> found) {
        if (node instanceof ScriptCall call) {
            found.add(call);
            return;
        }
        if (node instanceof SequenceNode sequence) {
            sequence.steps().forEach(step -> collect(step, found));
            return;
        }
        if (node instanceof FallbackNode fallback) {
            fallback.steps().forEach(step -> collect(step, found));
            return;
        }
        throw new IllegalArgumentException("Узел " + node.getClass().getSimpleName() + " не разбирается тестом");
    }
}
