package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.movement.PitExitPlanner;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.PlanValidator;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.CoreScripts;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RescueHelperPlanTests {
    private static final BlockCoord TRAPPED = new BlockCoord(10, 40, -5);
    private static final BlockCoord HOME = new BlockCoord(10, 64, -5);

    @Test
    void helper_WalkUpAfterClear_NoDig() {
        PitExitPlanner.Plan escape = new PitExitPlanner.Plan(
                PitExitPlanner.Method.WALK, new BlockCoord(10, 41, -5), 0, List.of(), List.of(), "выход пешком");

        TaskNode plan = RescuePlans.helper(TRAPPED, HOME, escape);

        PlanValidator.validate(plan, CoreScripts.defaultCatalog(), ConditionCatalog.empty());
        assertEquals(
                List.of("goto", "wait", "clear_area", "goto"),
                scripts(plan),
                "Когда после clear_area есть пеший выход, помощник не копает и не ставит столб зря");
    }

    @Test
    void helper_NoWalkUpAfterClear_GetsStairs() {
        PitExitPlanner.Plan escape = new PitExitPlanner.Plan(
                PitExitPlanner.Method.STAIRS, new BlockCoord(10, 46, -5), 6, List.of(), List.of(), "ступени в стене");

        TaskNode plan = RescuePlans.helper(TRAPPED, HOME, escape);

        PlanValidator.validate(plan, CoreScripts.defaultCatalog(), ConditionCatalog.empty());
        assertTrue(
                scripts(plan).contains("dig_out"),
                "Спасённого из пещеры после clear_area не вывести шагом — помощник обязан пробить ступени, "
                        + "иначе задание закрывается, а дух остаётся внизу (debtnav, п.3): " + scripts(plan));
    }

    @Test
    void helper_NoWalkUpAfterClear_GetsPillar() {
        PitExitPlanner.Plan escape = new PitExitPlanner.Plan(
                PitExitPlanner.Method.PILLAR, new BlockCoord(10, 43, -5), 3, List.of(), List.of(), "столб");

        TaskNode plan = RescuePlans.helper(TRAPPED, HOME, escape);

        PlanValidator.validate(plan, CoreScripts.defaultCatalog(), ConditionCatalog.empty());
        assertTrue(
                scripts(plan).contains("pillar_up"),
                "Из узкой ямы после вскрытия верх остаётся недоступным — помощник ставит столб (debtnav, п.3): "
                        + scripts(plan));
    }

    private static List<String> scripts(TaskNode plan) {
        List<String> names = new ArrayList<>();
        collect(plan, names);
        return names;
    }

    private static void collect(TaskNode plan, List<String> names) {
        if (plan instanceof ScriptCall call) {
            names.add(call.script());
        } else if (plan instanceof SequenceNode sequence) {
            sequence.steps().forEach(step -> collect(step, names));
        } else if (plan instanceof FallbackNode fallback) {
            fallback.steps().forEach(step -> collect(step, names));
        }
    }
}
