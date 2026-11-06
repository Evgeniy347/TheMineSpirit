package com.theminespirit.neoforge.movement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SwimStepTests {
    @Test
    void far_SmallLag_IsAccepted() {
        assertFalse(SwimStep.far(1.8, 0.4), "лаг меньше двух клеток — дух ещё идёт к шагу, а не телепортировался");
        assertFalse(SwimStep.far(2.5, 2.5), "сдвиг на две клетки по диагонали допустим после падения");
    }

    @Test
    void far_Teleport_IsRejected() {
        assertTrue(SwimStep.far(2.7, 0), "шаг дальше двух клеток — уже не соседняя клетка");
        assertTrue(SwimStep.far(0, 3.0), "вертикальный обрыв не маскирует скачок по Z");
    }
}
