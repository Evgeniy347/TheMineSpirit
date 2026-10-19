package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StuckWatcherTests {
    @Test
    void observe_FirstSample_IsNotStuck() {
        StuckWatcher watcher = new StuckWatcher(0.05, 3);

        assertFalse(
                watcher.observe(10, 64, 10),
                "первое наблюдение задаёт точку отсчёта и не может быть застреванием: иначе дух падал бы "
                        + "в первый же тик");
    }

    @Test
    void observe_PositionUnchangedForStuckTicks_ReportsStuck() {
        StuckWatcher watcher = new StuckWatcher(0.05, 3);
        watcher.observe(10, 64, 10);

        assertFalse(
                watcher.observe(10, 64, 10), "после первого одинакового наблюдения порог застревания ещё не достигнут");
        assertFalse(
                watcher.observe(10, 64, 10), "после второго одинакового наблюдения порог застревания ещё не достигнут");
        assertTrue(
                watcher.observe(10, 64, 10),
                "позиция, не менявшаяся три тика, — застревание: иначе goto бесконечно стоит у стены");
    }

    @Test
    void observe_PositionMovesEveryTick_IsNeverStuck() {
        StuckWatcher watcher = new StuckWatcher(0.05, 2);
        watcher.observe(0, 64, 0);

        assertFalse(watcher.observe(1, 64, 0), "сместившийся дух не застревание");
        assertFalse(watcher.observe(2, 64, 0), "дух, идущий каждый тик, не застревание, сколько бы тиков ни прошло");
    }

    @Test
    void observe_MovementAfterStillness_ResetsCounter() {
        StuckWatcher watcher = new StuckWatcher(0.05, 2);
        watcher.observe(0, 64, 0);
        watcher.observe(0, 64, 0);

        assertFalse(watcher.observe(1, 64, 0), "возобновившееся движение сбрасывает счётчик простоя");
        assertFalse(watcher.observe(1, 64, 0), "после сброса нужно снова набрать полный порог одинаковых тиков");
    }

    @Test
    void constructor_NonPositiveMovement_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new StuckWatcher(0, 2),
                "Нулевой порог движения обязан отклоняться: на нём любой шум позиции считается застреванием");

        assertTrue(
                error.getMessage().contains("0"),
                "Отказ обязан называть порог '0', чтобы видеть, какое значение перепроверить. Фактическое: "
                        + error.getMessage());
    }

    @Test
    void constructor_StuckTicksBelowOne_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new StuckWatcher(0.05, 0),
                "Порог застревания меньше единицы обязан отклоняться: детект сработал бы до наблюдения");
    }
}
