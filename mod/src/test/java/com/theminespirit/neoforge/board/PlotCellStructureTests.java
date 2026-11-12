package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlotCellStructureTests {
    @Test
    void spiritFill_OutsidePlan_IsNotStructure() {
        assertFalse(
                PlotCellStructure.structure(false, PlotCellStructure.Mark.SPIRIT),
                "насыпь/подмости духа вне схем — не блок плана (сервер 12.11: расчистка получала отказ BREAK,"
                        + " восстановление возвращало срезанное)");
    }

    @Test
    void spiritBlock_InPlan_IsStructure() {
        assertTrue(PlotCellStructure.structure(true, PlotCellStructure.Mark.SPIRIT), "блок схемы остаётся планом");
    }
}
