package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.TaskNode;
import org.junit.jupiter.api.Test;

class TicketPlanPickTests {
    private static final BlockCoord AT = new BlockCoord(2, 65, 7);

    @Test
    void explicitPlan_PlanGiven_WinsOverBoardCasePlan() {
        TaskNode explicit = MorePlans.returnHome();

        TaskNode plan = TicketPlanPick.plan(
                BoardCase.ACTIVITY_RETURN, 1, TicketTarget.at(AT).withPlan(explicit));

        assertEquals(
                explicit,
                plan,
                "Явный план задания исполняется вместо плана по кейсу: иначе действие "
                        + "more нельзя связать с доской");
    }

    @Test
    void boardCase_NoExplicitPlan_UsesCasePlan() {
        TaskNode plan = TicketPlanPick.plan(BoardCase.COLLECT_DROPS, 1, TicketTarget.at(AT));

        assertNotEquals(
                MorePlans.returnHome(),
                plan,
                "Без явного плана задание собирается по кейсу доски, а не подменяется чужим действием");
    }
}
