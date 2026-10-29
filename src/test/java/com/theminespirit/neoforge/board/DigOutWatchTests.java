package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class DigOutWatchTests {
    @Test
    void stateKey_AirSkyOrTaskChanged_StaysSame() {
        DigOutWatch.Check first = new DigOutWatch.Check(false, false, false, 7, true, "chop_tree");
        DigOutWatch.Check second = new DigOutWatch.Check(false, false, false, 1, false, "goto");

        assertEquals(
                first.stateKey(),
                second.stateKey(),
                "Высота воздуха и задача не меняют состояние замурованности — иначе «проверка … false» пишется "
                        + "на каждый шаг и тонет в шуме");
    }

    @Test
    void stateKey_SealedOrDiggingChanged_IsNewState() {
        DigOutWatch.Check clear = new DigOutWatch.Check(false, false, false, 3, false, "goto");
        DigOutWatch.Check sealed = new DigOutWatch.Check(true, false, false, 3, false, "goto");
        DigOutWatch.Check digging = new DigOutWatch.Check(false, true, false, 3, false, "dig_out");

        assertNotEquals(clear.stateKey(), sealed.stateKey(), "Замурованность — смена состояния");
        assertNotEquals(clear.stateKey(), digging.stateKey(), "Начало копания — смена состояния");
    }
}
