package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
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

class RescuePlansTests {
    private static final BlockCoord TRAPPED = new BlockCoord(10, 40, -5);

    @Test
    void digToHome_TargetsHomePoint_NotRoofAboveColumn() {
        BlockCoord home = new BlockCoord(-171, 72, -489);

        TaskNode plan = RescuePlans.digToHome(home);
        assertTrue(
                plan instanceof ScriptCall,
                "Выход с киркой — один вызов dig_out: лишний шаг сдвинул бы цель Иначе тест digToHome_TargetsHomePoint_NotRoofAboveColumn перестанет ловить эту поломку. При смене digToHome_TargetsHomePoint_NotRoofAboveColumn проверь это утверждение.");
        ScriptCall call = (ScriptCall) plan;
        assertEquals(
                "dig_out",
                call.script(),
                "Выход копает скриптом dig_out Иначе тест digToHome_TargetsHomePoint_NotRoofAboveColumn перестанет ловить эту поломку. При смене digToHome_TargetsHomePoint_NotRoofAboveColumn проверь это утверждение.");
        assertEquals(
                home.x(),
                intParam(call, "x"),
                "Цель dig_out по x — точка дома Иначе тест digToHome_TargetsHomePoint_NotRoofAboveColumn перестанет ловить эту поломку. При смене digToHome_TargetsHomePoint_NotRoofAboveColumn проверь это утверждение.");
        assertEquals(
                home.y(),
                intParam(call, "y"),
                "Цель dig_out по y — точка дома, а не верх колонки: крыша склада над домом увела бы духа наверх Иначе тест digToHome_TargetsHomePoint_NotRoofAboveColumn перестанет ловить эту поломку. При смене digToHome_TargetsHomePoint_NotRoofAboveColumn проверь это утверждение.");
        assertEquals(
                home.z(),
                intParam(call, "z"),
                "Цель dig_out по z — точка дома Иначе тест digToHome_TargetsHomePoint_NotRoofAboveColumn перестанет ловить эту поломку. При смене digToHome_TargetsHomePoint_NotRoofAboveColumn проверь это утверждение.");
    }

    @Test
    void plan_RescueCase_ReturnsPlan() {
        TaskNode plan = RescuePlans.plan(BoardCase.RESCUE_SPIRIT, TicketTarget.at(TRAPPED))
                .orElseThrow();
        assertTrue(
                plan instanceof SequenceNode,
                "спасение — последовательность: подойти, вскрыть клетку, подойти Иначе тест plan_RescueCase_ReturnsPlan перестанет ловить эту поломку. При смене plan_RescueCase_ReturnsPlan проверь это утверждение.");
    }

    @Test
    void plan_OtherCase_IsEmpty() {
        assertTrue(
                RescuePlans.plan(BoardCase.REPAIR_FENCE, TicketTarget.at(TRAPPED))
                        .isEmpty(),
                "чужой кейс не получает план спасения: иначе доска подменит ремонт ограды При смене plan_OtherCase_IsEmpty проверь это утверждение.");
    }

    @Test
    void plan_RescueCase_PassesCatalog() {
        TaskNode plan = RescuePlans.plan(BoardCase.RESCUE_SPIRIT, TicketTarget.at(TRAPPED))
                .orElseThrow();
        PlanValidator.validate(plan, CoreScripts.defaultCatalog(), ConditionCatalog.empty());
        assertEquals(
                List.of("goto", "wait", "clear_area"),
                scripts(plan),
                "план спасения проходит каталог: подойти и вскрыть клетку вокруг духа; копать вниз к духу "
                        + "запрещено (observe24 B3) Иначе тест plan_RescueCase_PassesCatalog перестанет ловить эту поломку. При смене plan_RescueCase_PassesCatalog проверь это утверждение.");
    }

    private static int intParam(ScriptCall call, String key) {
        Object value = call.params().get(key);
        assertTrue(
                value instanceof Number,
                "У dig_out нет числа в параметре " + key
                        + ": цель не задать Иначе тест plan_RescueCase_PassesCatalog перестанет ловить эту поломку. При смене plan_RescueCase_PassesCatalog проверь это утверждение.");
        return ((Number) value).intValue();
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
