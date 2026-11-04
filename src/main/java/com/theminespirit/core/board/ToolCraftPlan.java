package com.theminespirit.core.board;

import com.theminespirit.core.board.graph.GraphNames;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.building.WoodSpecies;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.script.CatalogLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class ToolCraftPlan {
    private static final String ITEM = "item";
    private static final String COUNT = "count";
    private static final String CRAFT = "craft";

    private ToolCraftPlan() {}

    static TaskNode of(BoardCase boardCase, TicketTarget target) {
        return ofItem(ToolSet.itemId(boardCase), target);
    }

    static TaskNode ofItem(String item, TicketTarget target) {
        int count = Math.min(
                target.toolCount() > 0 ? target.toolCount() : Math.max(1, target.count()), CatalogLimits.COUNT_MAX);
        List<TaskNode> steps = new ArrayList<>(4);
        steps.add(TicketPlans.gotoTarget(target));
        steps.addAll(materials(item, count, target.wood()));
        steps.add(new ScriptCall(
                CRAFT, Map.of(ITEM, item, COUNT, count, "radius", CatalogLimits.STATION_RADIUS_DEFAULT)));
        return TicketPlans.withLeftovers(new SequenceNode(steps), target);
    }

    private static List<TaskNode> materials(String item, int tools, @Nullable String wood) {
        SupplyGraph graph = DataTables.INSTANCE.graph().current();
        Optional<SupplyGraph.Way> recipe =
                graph.producers(GraphNames.graphItem(item)).stream().findFirst();
        if (recipe.isEmpty()) {
            return List.of();
        }
        String species = wood == null ? WoodSpecies.OAK : wood;
        List<TaskNode> steps = new ArrayList<>();
        recipe.get().consumes().forEach((input, amount) -> {
            String material = GraphNames.item(graph, input, species);
            steps.add(PlanSteps.acquire(material, amount * tools));
        });
        return steps;
    }
}
