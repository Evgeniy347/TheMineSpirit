package com.theminespirit.core.route;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RouteFailuresTests {
    private static final UUID SPIRIT = UUID.fromString("f58b83b6-c9a0-406d-967a-822843debf54");
    private static final BlockCoord FROM = new BlockCoord(-104, -20, -412);
    private static final BlockCoord TARGET = new BlockCoord(-171, 69, -489);

    @Test
    void recentlyFailed_SameRequestWithinTtl_SuppressesSecondSearch() {
        RouteFailures failures = new RouteFailures();
        RouteFailures.Key key = new RouteFailures.Key(SPIRIT, FROM, TARGET, 2, 1);

        assertFalse(failures.recentlyFailed(key, 1000, 1200), "До первого провала кэш пуст");
        failures.remember(key, 1000);

        assertTrue(
                failures.recentlyFailed(key, 1540, 1200),
                "Тот же запрос через 540 тиков (как повтор подхода в observe15) не считает маршрут заново");
    }

    @Test
    void recentlyFailed_AfterTtl_AllowsNewSearch() {
        RouteFailures failures = new RouteFailures();
        RouteFailures.Key key = new RouteFailures.Key(SPIRIT, FROM, TARGET, 2, 1);
        failures.remember(key, 1000);

        assertFalse(
                failures.recentlyFailed(key, 2200, 1200),
                "После срока кэш истекает: мир мог измениться, маршрут пересчитывается");
    }

    @Test
    void recentlyFailed_OtherStartOrTarget_IsNotSuppressed() {
        RouteFailures failures = new RouteFailures();
        failures.remember(new RouteFailures.Key(SPIRIT, FROM, TARGET, 2, 1), 1000);

        assertFalse(
                failures.recentlyFailed(
                        new RouteFailures.Key(SPIRIT, new BlockCoord(-103, -35, -396), TARGET, 2, 1), 1001, 1200),
                "Другой старт — другой расчёт: дух сдвинулся, старый провал к нему не относится");
        assertFalse(
                failures.recentlyFailed(
                        new RouteFailures.Key(SPIRIT, FROM, new BlockCoord(-167, 70, -500), 2, 1), 1001, 1200),
                "Другая цель — другой расчёт: цель могла быть достижимой");
    }

    @Test
    void forget_Spirit_DropsItsRecordsOnly() {
        RouteFailures failures = new RouteFailures();
        UUID other = UUID.fromString("caf11c6f-9a9a-408d-92ed-71726c762c19");
        failures.remember(new RouteFailures.Key(SPIRIT, FROM, TARGET, 2, 1), 1000);
        failures.remember(new RouteFailures.Key(other, FROM, TARGET, 2, 1), 1000);

        failures.forget(SPIRIT);

        assertFalse(
                failures.recentlyFailed(new RouteFailures.Key(SPIRIT, FROM, TARGET, 2, 1), 1001, 1200),
                "Успешный маршрут духа стирает его провалы: иначе удачный путь не запомнится как рабочий");
        assertTrue(
                failures.recentlyFailed(new RouteFailures.Key(other, FROM, TARGET, 2, 1), 1001, 1200),
                "Записи чужих духов не стираются: кэш ведётся по духу");
    }
}
