package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.town.PlotLeveling;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

final class RoutePlans {
    private static final String BLOCK = "block";
    private static final String ITEM = "item";
    private static final String COUNT = "count";
    private static final String CONTAINER = "container";
    private static final String NEAREST = "nearest";
    private static final String RADIUS = "radius";
    private static final String FILL = "fill";
    private static final String ACQUIRE = "acquire";
    private static final String CLEAR_AREA = "clear_area";
    private static final String DEPOSIT = "deposit";
    private static final String MINECRAFT = "minecraft:";
    private static final int SUPPLY_CONTAINER_RADIUS = 8;

    private RoutePlans() {}

    static TaskNode path(TicketTarget target) {
        PlotLeveling plan = target.leveling();
        if (plan == null || target.from() == null) {
            return new SequenceNode(List.of(PlanSteps.gotoPoint(target.target())));
        }
        List<TaskNode> steps = new ArrayList<>();
        steps.add(PlanSteps.gotoPoint(target.from()));
        int volume = plan.fillVolume();
        for (int left = volume; left > 0; left -= CatalogLimits.COUNT_MAX) {
            steps.add(new ScriptCall(
                    ACQUIRE, Map.of(ITEM, full(plan.block()), COUNT, Math.min(left, CatalogLimits.COUNT_MAX))));
        }
        steps.add(GatherPlans.shovel());
        plan.cutTopDown().forEach(box -> steps.add(new ScriptCall(CLEAR_AREA, boxParams(box, null))));
        plan.fill().forEach(box -> steps.add(new ScriptCall(FILL, boxParams(box, plan.block()))));
        return new SequenceNode(steps);
    }

    static TaskNode dig(TicketTarget target) {
        PlotLeveling plan = Objects.requireNonNull(
                target.leveling(), "Добыча с маршрута без плана слоёв: нечего снимать с участка");
        List<TaskNode> steps = new ArrayList<>();
        BlockCoord dig = target.from() == null ? target.target() : target.from();
        steps.add(PlanSteps.gotoPoint(dig));
        steps.add(GatherPlans.shovel());
        plan.cutTopDown().forEach(box -> steps.add(new ScriptCall(CLEAR_AREA, boxParams(box, null))));
        steps.add(new ScriptCall("collect_drops", Map.of(RADIUS, CatalogLimits.DROP_RADIUS_MAX)));
        steps.add(PlanSteps.gotoPoint(target.target()));
        if (target.supplyItem() != null) {
            steps.add(new ScriptCall(
                    DEPOSIT,
                    Map.of(
                            CONTAINER,
                            NEAREST,
                            ITEM,
                            target.supplyItem(),
                            COUNT,
                            Math.min(Math.max(1, target.supplyCount()), CatalogLimits.COUNT_MAX),
                            RADIUS,
                            SUPPLY_CONTAINER_RADIUS)));
        }
        return new SequenceNode(steps);
    }

    private static Map<String, Object> boxParams(PlotLeveling.Box box, @Nullable String block) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("x1", box.x1());
        params.put("y1", box.y1());
        params.put("z1", box.z1());
        params.put("x2", box.x2());
        params.put("y2", box.y2());
        params.put("z2", box.z2());
        if (block != null) {
            params.put(BLOCK, full(block));
        }
        return Map.copyOf(params);
    }

    private static String full(String block) {
        return block.contains(":") ? block : MINECRAFT + block;
    }
}
