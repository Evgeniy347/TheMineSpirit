package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.town.PlotLeveling;
import java.util.List;
import org.junit.jupiter.api.Test;

class GroundsAuditTests {
    private static final PlotLeveling MOUND =
            new PlotLeveling(List.of(new PlotLeveling.Box(0, 70, 0, 0, 75, 0)), List.of(), PlotLeveling.DEFAULT_BLOCK);

    @Test
    void uneven_MoundLeft_ClearingStepStaysOpen() {
        assertTrue(
                GroundsAudit.uneven(BoardCase.CLEAR_GROUNDS, MOUND),
                "Есть что срезать — расчистка не готова: аудит рельефа по плану выравнивания, а не по схеме "
                        + "(у рельефа схемы нет, «0 из 0 блоков» закрывал шаг)");
    }

    @Test
    void uneven_FlatOrOtherCase_NoReopenByGround() {
        assertFalse(GroundsAudit.uneven(BoardCase.CLEAR_GROUNDS, PlotLeveling.FLAT), "Ровный участок — готово");
        assertFalse(GroundsAudit.uneven(BoardCase.BUILD_HOUSE, MOUND), "Рельеф решает только для расчистки");
    }
}
