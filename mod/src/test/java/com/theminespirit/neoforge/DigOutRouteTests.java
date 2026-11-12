package com.theminespirit.neoforge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DigOutRouteTests {
    @Test
    void mode_SpiritBobsWhileFollowingRoute_RouteKept() {
        assertFalse(
                DigOutRoute.mode(70, 71, false, true),
                "Дух на ступеньке качается между y=70 и 71 у дома на y=70, путь уже идёт — режим не переключается: "
                        + "иначе готовый путь отменялся и искался заново по кругу (сервер 12.11, поиски до 85 с)");
    }

    @Test
    void mode_NoRoute_DecidedByHeight() {
        assertTrue(DigOutRoute.mode(70, 71, false, false), "Пути нет, дом ниже — новый путь вниз");
        assertFalse(DigOutRoute.mode(71, 70, true, false), "Пути нет, дом не ниже — новый путь к дому");
    }
}
