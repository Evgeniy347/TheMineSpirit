package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RepairPlanTests {
    private static final BlockCoord ANCHOR = new BlockCoord(100, 64, -40);

    @Test
    void target_FencePlan_RepairBySchemeAtFenceOrigin() {
        TicketTarget built = TicketTarget.at(ANCHOR).withWood("jungle");

        Optional<TicketTarget> repair = RepairPlan.target(TicketPlans.plan(BoardCase.BUILD_FENCE, 1, built));

        assertEquals(
                Optional.of(SettlementLayout.fenceOrigin(ANCHOR)),
                repair.map(TicketTarget::target),
                "Восстановление ограды — по её схеме от угла участка: иначе REPAIR_BUILDING строит не там (INC-60)");
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
}
