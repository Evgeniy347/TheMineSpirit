package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class SettlementPlotTests {
    @Test
    void center_NoHomes_FailsWithReason() {
        IllegalArgumentException refusal = assertThrows(
                IllegalArgumentException.class,
                () -> SettlementPlot.center(List.of()),
                "Центр поселения без домов — ошибка, а не выдуманная точка: иначе вызывающий молча уходит в "
                        + "пустой угол мира");

        assertTrue(
                refusal.getMessage().contains("Нет ни одного дома"),
                "Центр поселения без домов не выдумывается: исключение называет причину, иначе вызывающий "
                        + "молча получит координату из воздуха; получено: " + refusal.getMessage());
    }

    @Test
    void center_SpiritOrder_DoesNotMoveHome() {
        BlockCoord first = new BlockCoord(-171, 72, -497);
        BlockCoord second = new BlockCoord(-165, 72, -491);
        BlockCoord third = new BlockCoord(-168, 72, -494);

        assertEquals(
                SettlementPlot.center(List.of(first, second, third)),
                SettlementPlot.center(List.of(third, first, second)),
                "Точка поселения не зависит от порядка духов: иначе сцена и цель выхода меняются от "
                        + "порядка сущностей в мире");
    }
}
