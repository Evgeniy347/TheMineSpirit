package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FencePlanTests {
    private static final TicketTarget TARGET = TicketTarget.at(new BlockCoord(10, 60, -5));

    @Test
    void plan_BuildFence_DoesNotGotoTargetFirst() {
        TaskNode plan = TicketPlans.plan(BoardCase.BUILD_FENCE, TARGET);

        assertEquals(
                "build_schematic",
                ((ScriptCall) plan).script(),
                "Перед схемой забора нет отдельного перехода к якорю: схема сама ищет стоянку, а подход "
                        + "к якорю (−171,70,−523) проваливался «перенос не найден» при плане 100% "
                        + "(наблюдение 7). Задание с исполненным планом обязано закрываться DONE");
    }

    @Test
    void plan_NoFencePart_KeepsFullRingForRestDemo() {
        ScriptCall plan = (ScriptCall) TicketPlans.plan(BoardCase.BUILD_FENCE, TARGET);

        assertEquals(
                "fence",
                ((Map<?, ?>) plan.params()).get("file"),
                "Задание без схемы и номера части остаётся кольцом 23×23: это путь REST/демо, его не меняем");
    }

    @Test
    void plan_NumberedFencePart_UsesQuarterRing() {
        ScriptCall plan = (ScriptCall) TicketPlans.plan(BoardCase.BUILD_FENCE, TARGET.withCount(2));

        assertEquals(
                "fence#2/4",
                ((Map<?, ?>) plan.params()).get("file"),
                "Номер части по-прежнему делит кольцо на четыре куска: план этапа не должен потерять порционность");
    }
}
