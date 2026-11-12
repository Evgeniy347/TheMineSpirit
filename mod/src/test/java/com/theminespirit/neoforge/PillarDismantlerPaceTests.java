package com.theminespirit.neoforge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PillarDismantlerPaceTests {
    @Test
    void ready_StillFalling_Waits() {
        assertFalse(
                PillarDismantler.ready(100, false),
                "Дух ещё падает после прошлого блока — следующий не ломается: иначе он разбирает столб быстрее, "
                        + "чем опускается, и падает с высоты (сервер 12.11, урон от падения)");
    }

    @Test
    void ready_LandedButTooSoon_Waits() {
        assertFalse(PillarDismantler.ready(PillarDismantler.BREAK_INTERVAL_TICKS - 1, true), "Не чаще блока в секунду");
        assertTrue(PillarDismantler.ready(PillarDismantler.BREAK_INTERVAL_TICKS, true), "Стоит и прошла секунда");
    }
}
