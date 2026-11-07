package com.theminespirit.core.board;

import com.theminespirit.core.building.FillOrder;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.town.PlotLeveling;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class PitFillPlan {
    private static final int CRATER_FILL_RADIUS = 1;
    private static final String ITEM = "item";
    private static final String COUNT = "count";
    private static final String BLOCK = "block";
    private static final String FILL = "fill";
    private static final String ACQUIRE = "acquire";
    private static final String DIRT = "dirt";

    private PitFillPlan() {}

    static TaskNode plan(TicketTarget target) {
        PlotLeveling leveling = target.leveling();
        if (leveling != null && !leveling.fill().isEmpty()) {
            return pitFill(target, leveling);
        }
        int blocks = target.craterBlocks() > 0
                ? target.craterBlocks()
                : craterBlocksEstimate(BoardTuning.CRATER_BLOCKS_ESTIMATE_RADIUS);
        int depth = Math.max(1, Math.min(blocks, CatalogLimits.WORLD_Y_MAX - CatalogLimits.WORLD_Y_MIN));
        int bottom = Math.max(CatalogLimits.WORLD_Y_MIN, target.target().y() - depth + 1);
        return new SequenceNode(List.of(
                TicketPlans.gotoTarget(target),
                new ScriptCall(ACQUIRE, Map.of(ITEM, DIRT, COUNT, Math.min(blocks, CatalogLimits.COUNT_MAX))),
                fillArea(target, bottom)));
    }

    private static TaskNode pitFill(TicketTarget target, PlotLeveling leveling) {
        List<TaskNode> steps = new ArrayList<>();
        steps.add(TicketPlans.gotoTarget(target));
        List<FillOrder.Box> layers = FillOrder.layers(leveling.fill());
        int batch = Math.min(LevelingRules.current().batch(), CatalogLimits.COUNT_MAX);
        for (List<FillOrder.Box> group : FillOrder.batches(layers, batch)) {
            steps.add(new ScriptCall(
                    ACQUIRE, Map.of(ITEM, DIRT, COUNT, Math.min(FillOrder.volume(group), CatalogLimits.COUNT_MAX))));
            for (FillOrder.Box layer : group) {
                steps.add(new ScriptCall(FILL, GroundsPlan.boxParams(layer, Map.of(BLOCK, DIRT))));
            }
        }
        return new SequenceNode(steps);
    }

    private static ScriptCall fillArea(TicketTarget target, int bottom) {
        return new ScriptCall(
                FILL,
                Map.of(
                        BLOCK,
                        DIRT,
                        "x1",
                        target.target().x() - CRATER_FILL_RADIUS,
                        "y1",
                        bottom,
                        "z1",
                        target.target().z() - CRATER_FILL_RADIUS,
                        "x2",
                        target.target().x() + CRATER_FILL_RADIUS,
                        "y2",
                        target.target().y(),
                        "z2",
                        target.target().z() + CRATER_FILL_RADIUS));
    }

    private static int craterBlocksEstimate(int radius) {
        return (int) Math.ceil(Math.PI * radius * radius);
    }
}
