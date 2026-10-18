package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CellCenteringTests {
    @Test
    void centering_AtCellCenter_True() {
        assertTrue(CellCentering.centered(0, 0),
                "Дух ровно в центре клетки обязан считаться стоящим в ней: иначе подход к столбу не закончится");
    }

    @Test
    void centering_InsideTolerance_True() {
        assertTrue(CellCentering.centered(0.14, 0.05),
                "Сдвиг 0.149 меньше порога 0.15: такой запас ещё не даёт голове задеть ствол на прыжке");
    }

    @Test
    void centering_AtTolerance_True() {
        assertTrue(CellCentering.centered(CellCentering.TOLERANCE, 0),
                "Граница порога включается: иначе подход к столбу мог бы никогда не завершиться на точном значении");
    }

    @Test
    void centering_BeyondTolerance_False() {
        assertFalse(CellCentering.centered(0.2, 0),
                "Сдвиг 0.2 к стволу оставляет голове 0 блока запаса — дух прижмётся к бревну и не допрыгнет");
    }

    @Test
    void centering_DiagonalBeyondTolerance_False() {
        assertFalse(CellCentering.centered(0.15, 0.15),
                "Радиус, а не максимум по осям: диагональный сдвиг 0.21 больше порога и тоже прижимает к стволу");
    }

    @Test
    void centering_Tolerance_IsFifteenHundredths() {
        assertEquals(0.15, CellCentering.TOLERANCE,
                "Порог 0.15: запас до границы клетки 0.2 блока (0.5 полуширины минус 0.3 полуширины духа) "
                        + "переживает сдвиг до 0.15; больше — голова задевает бревно ствола на прыжке");
    }
}
