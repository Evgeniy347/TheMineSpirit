package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
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
}
