package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RepairPlanTests {
    private static final BlockCoord ANCHOR = new BlockCoord(100, 64, -40);

    @Test
    void target_FencePlan_RepairBySchemeAtFenceOrigin() {
        TicketTarget built = TicketTarget.at(ANCHOR).withWood("jungle");

        Optional<TicketTarget> repair = RepairPlan.target(TicketPlans.plan(BoardCase.BUILD_FENCE, 1, built));

        assertEquals(
                Optional.of(ANCHOR),
                repair.map(TicketTarget::target),
                "Восстановление ограды без плана — по её схеме от цели задания: иначе REPAIR_BUILDING строит не там (INC-60)");
        assertEquals(Optional.of("fence"), repair.map(TicketTarget::schemeFile), "Восстановление ограды — схема fence");
        assertEquals(
                Optional.of("jungle"),
                repair.map(TicketTarget::wood),
                "Порода плана сохраняется: восстановление jungle-ограды не ставит дуб (INC-58)");
    }

    @Test
    void target_LightingPlan_NoRepairScheme() {
        assertTrue(
                RepairPlan.target(TicketPlans.plan(BoardCase.LIGHTING, 1, TicketTarget.at(ANCHOR)))
                        .isEmpty(),
                "У света нет схемы: его DONE при пустом мире снова ставит шаг света, а не восстановление");
    }

    private static BoardTicket repair(String id) {
        return new BoardTicket(
                id,
                BoardCase.REPAIR_BUILDING,
                Optional.empty(),
                BoardRules.priorityOf(BoardCase.REPAIR_BUILDING),
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());
    }

    @Test
    void goalless_RepairWithoutTargetAfterSchemeChange_True() {
        assertTrue(
                RepairPlan.goalless(repair("repair:build_house"), null)
                        && RepairPlan.goalless(repair("repair:level:14419043"), TicketTarget.at(ANCHOR)),
                "Ремонт без схемы, выравнивания и места этапа снимается, а не висит с ERROR «план не собран» "
                        + "(INC-93: старые ремонты после смены схем)");
    }

    @Test
    void goalless_RepairWithScheme_False() {
        assertFalse(
                RepairPlan.goalless(
                        repair("repair:build_house"), TicketTarget.at(ANCHOR).withScheme("fence")),
                "Ремонт со схемой — живая цель: его не снимают");
    }
}
