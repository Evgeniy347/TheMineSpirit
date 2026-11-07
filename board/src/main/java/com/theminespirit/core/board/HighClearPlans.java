package com.theminespirit.core.board;

import com.theminespirit.core.building.ScaffoldPlan;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CatalogLimits;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class HighClearPlans {
    private HighClearPlans() {}

    static TaskNode plan(TicketTarget target) {
        ScaffoldPlan.Scaffold scaffold = target.scaffold();
        if (scaffold == null) {
            throw new IllegalArgumentException("Кейс CLEAR_HIGH без подмости: задание не знает, откуда и что сносить");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("scaffold", scaffold.id());
        params.put("x", scaffold.anchor().x());
        params.put("y", scaffold.anchor().y());
        params.put("z", scaffold.anchor().z());
        params.put("height", scaffold.height());
        params.put("block", "auto");
        params.put("cells", points(scaffold.targetsTopDown()));
        params.put("tower", points(scaffold.dismantle()));
        return new SequenceNode(List.of(
                PlanSteps.gotoPoint(scaffold.anchor()),
                PlanSteps.acquire("minecraft:dirt", Math.max(1, Math.min(CatalogLimits.COUNT_MAX, scaffold.height()))),
                new ScriptCall("clear_high", Map.copyOf(params))));
    }

    private static List<String> points(List<BlockCoord> cells) {
        return cells.stream()
                .map(cell -> cell.x() + "," + cell.y() + "," + cell.z())
                .toList();
    }
}
