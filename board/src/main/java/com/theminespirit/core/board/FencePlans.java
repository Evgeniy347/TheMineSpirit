package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CatalogLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class FencePlans {
    private static final String FILL = "fill";
    private static final String BLOCK = "block";
    private static final String WAIT = "wait";
    private static final String TICKS = "ticks";
    private static final String DEFAULT_BLOCK = "oak_fence";

    private FencePlans() {}

    static TaskNode repair(TicketTarget target) {
        if (target.fenceCells().isEmpty()) {
            String block = target.fenceBlock() == null ? DEFAULT_BLOCK : target.fenceBlock();
            return new SequenceNode(List.of(moveTo(target.target()), cell(target.target(), block)));
        }
        List<TaskNode> steps = new ArrayList<>();
        steps.add(moveTo(target.fenceCells().get(0)));
        for (int index = 0; index < target.fenceCells().size(); index++) {
            steps.add(cell(
                    target.fenceCells().get(index), target.fenceCellBlocks().get(index)));
        }
        return new SequenceNode(List.copyOf(steps));
    }

    private static TaskNode cell(BlockCoord cell, String block) {
        return new FallbackNode(List.of(
                new ScriptCall(FILL, fill(cell, block)),
                new ScriptCall(WAIT, Map.of(TICKS, CatalogLimits.WAIT_TICKS_MIN))));
    }

    private static Map<String, Object> fill(BlockCoord cell, String block) {
        return Map.of(
                BLOCK, block, "x1", cell.x(), "y1", cell.y(), "z1", cell.z(), "x2", cell.x(), "y2", cell.y(), "z2",
                cell.z());
    }

    private static ScriptCall moveTo(BlockCoord point) {
        return new ScriptCall("goto", Map.of("x", point.x(), "y", point.y(), "z", point.z()));
    }
}
