package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class SettlementPlotTests {
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
