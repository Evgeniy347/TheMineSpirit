package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PathRefreshPolicyTests {
    @Test
    void shouldRefresh_FirstTarget_IsTrue() {
        PathRefreshPolicy policy = new PathRefreshPolicy();

        assertTrue(
                policy.shouldRefresh(10, 64, 10, false),
                "первый путь обязан строиться: без него дух не начнёт движение к цели");
    }

    @Test
    void shouldRefresh_TargetStayedWithinTolerance_IsFalse() {
        PathRefreshPolicy policy = new PathRefreshPolicy();
        policy.shouldRefresh(10, 64, 10, false);

        assertFalse(
                policy.shouldRefresh(10.5, 64, 10, false),
                "цель, сместившаяся меньше чем на блок, не требует нового поиска пути: иначе дух ищет путь "
                        + "каждый тик и кладёт серверный тик");
    }

    @Test
    void shouldRefresh_TargetMovedBeyondTolerance_IsTrue() {
        PathRefreshPolicy policy = new PathRefreshPolicy();
        policy.shouldRefresh(10, 64, 10, false);

        assertTrue(
                policy.shouldRefresh(12, 64, 10, false),
                "цель, ушедшая больше чем на блок, требует нового пути: иначе дух идёт к устаревшей точке "
                        + "и теряет игрока");
    }

    @Test
    void shouldRefresh_PathFinishedAfterActivePath_IsTrue() {
        PathRefreshPolicy policy = new PathRefreshPolicy();
        policy.shouldRefresh(10, 64, 10, false);
        policy.shouldRefresh(10, 64, 10, false);

        assertTrue(
                policy.shouldRefresh(10, 64, 10, true),
                "закончившаяся навигация требует нового пути, даже если цель не двигалась: иначе дух застынет "
                        + "в стороне от неподвижной цели");
    }

    @Test
    void shouldRefresh_NavigationAlwaysDoneWithoutActivePath_RetriesOnlyOnce() {
        PathRefreshPolicy policy = new PathRefreshPolicy();
        policy.shouldRefresh(10, 64, 10, false);

        assertTrue(
                policy.shouldRefresh(10, 64, 10, true),
                "первая неудачная попытка ещё допустима: у moveTo не было шанса дать путь (чанк мог быть не загружен)");
        assertFalse(
                policy.shouldRefresh(10, 64, 10, true),
                "повторная неудачная навигация не обязана строить путь каждый тик: иначе застрявший дух кладёт "
                        + "серверный тик, не имея ни одного шанса построить путь");
        assertFalse(
                policy.shouldRefresh(10, 64, 10, true),
                "кулдаун не успел пройти, дух обязан продолжать ждать, а не строить путь впустую");
    }

    @Test
    void shouldRefresh_FailedAttempt_RetriesAfterCooldown() {
        PathRefreshPolicy policy = new PathRefreshPolicy();
        policy.shouldRefresh(10, 64, 10, false);
        policy.shouldRefresh(10, 64, 10, true);

        for (int call = 0; call < PathRefreshPolicy.RETRY_COOLDOWN_CALLS; call++) {
            assertFalse(
                    policy.shouldRefresh(10, 64, 10, true),
                    "До исхода кулдауна рекомендация обязана быть отрицательной на вызове " + call);
        }
        assertTrue(
                policy.shouldRefresh(10, 64, 10, true),
                "после кулдауна дух обязан снова попробовать путь: чанк цели мог загрузиться, и без повторной "
                        + "попытки дух застрял бы навсегда");
    }

    @Test
    void shouldRefresh_NegativeTolerance_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new PathRefreshPolicy(-1),
                "Отрицательный допуск смещения обязан отклоняться: с ним ни один путь не строится");

        assertTrue(
                error.getMessage().contains("-1"),
                "Отказ обязан называть допуск '-1', чтобы видеть, какое значение перепроверить. Фактическое: "
                        + error.getMessage());
    }
}
