package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CellCenteringTests {
    @Test
    void centering_Tolerance_IsFifteenHundredths() {
        assertEquals(
                0.15,
                CellCentering.TOLERANCE,
                "Порог 0.15: запас до границы клетки 0.2 блока (0.5 полуширины минус 0.3 полуширины духа) "
                        + "переживает сдвиг до 0.15; больше — голова задевает бревно ствола на прыжке");
    }
}
