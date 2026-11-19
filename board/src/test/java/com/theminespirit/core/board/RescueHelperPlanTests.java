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
import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.script.api.ConditionCatalog;
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
        List<String> order = scripts(plan);
        int acquire = order.indexOf("acquire");
        assertTrue(
                acquire >= 0 && acquire < order.indexOf("pillar_up"),
                "Помощник идёт с пустыми руками: землю на столб он обязан взять до pillar_up, иначе спасение "
                        + "падает «нет материала для столба» (observe17): " + order);
        List<ScriptCall> calls = new ArrayList<>();
        collectCalls(plan, calls);
        ScriptCall taken = calls.stream()
                .filter(call -> "acquire".equals(call.script()))
                .findFirst()
                .orElseThrow();
        assertEquals("minecraft:dirt", taken.params().get("item"), "Столб ставится из земли: " + taken.params());
        assertEquals(3, taken.params().get("count"), "Земли берётся на весь столб высотой 3: " + taken.params());
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

    private static void collectCalls(TaskNode plan, List<ScriptCall> calls) {
        if (plan instanceof ScriptCall call) {
            calls.add(call);
        } else if (plan instanceof SequenceNode sequence) {
            sequence.steps().forEach(step -> collectCalls(step, calls));
        } else if (plan instanceof FallbackNode fallback) {
            fallback.steps().forEach(step -> collectCalls(step, calls));
        }
    }
}
