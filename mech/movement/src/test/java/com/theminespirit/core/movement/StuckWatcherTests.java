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
                        + "в первый же тик" + " При смене observe_FirstSample_IsNotStuck проверь это утверждение.");
    }

    @Test
    void observe_PositionUnchangedForStuckTicks_ReportsStuck() {
        StuckWatcher watcher = new StuckWatcher(0.05, 3);
        watcher.observe(10, 64, 10);

        assertFalse(
                watcher.observe(10, 64, 10),
                "после первого одинакового наблюдения порог застревания ещё не достигнут"
                        + " Иначе тест observe_PositionUnchangedForStuckTicks_ReportsStuck перестанет ловить эту поломку. При смене observe_PositionUnchangedForStuckTicks_ReportsStuck проверь это утверждение.");
        assertFalse(
                watcher.observe(10, 64, 10),
                "после второго одинакового наблюдения порог застревания ещё не достигнут"
                        + " Иначе тест observe_PositionUnchangedForStuckTicks_ReportsStuck перестанет ловить эту поломку. При смене observe_PositionUnchangedForStuckTicks_ReportsStuck проверь это утверждение.");
        assertTrue(
                watcher.observe(10, 64, 10),
                "позиция, не менявшаяся три тика, — застревание: иначе goto бесконечно стоит у стены"
                        + " При смене observe_PositionUnchangedForStuckTicks_ReportsStuck проверь это утверждение.");
    }

    @Test
    void observe_PositionMovesEveryTick_IsNeverStuck() {
        StuckWatcher watcher = new StuckWatcher(0.05, 2);
        watcher.observe(0, 64, 0);

        assertFalse(
                watcher.observe(1, 64, 0),
                "сместившийся дух не застревание"
                        + " Иначе тест observe_PositionMovesEveryTick_IsNeverStuck перестанет ловить эту поломку. При смене observe_PositionMovesEveryTick_IsNeverStuck проверь это утверждение.");
        assertFalse(
                watcher.observe(2, 64, 0),
                "дух, идущий каждый тик, не застревание, сколько бы тиков ни прошло"
                        + " Иначе тест observe_PositionMovesEveryTick_IsNeverStuck перестанет ловить эту поломку. При смене observe_PositionMovesEveryTick_IsNeverStuck проверь это утверждение.");
    }

    @Test
    void observe_MovementAfterStillness_ResetsCounter() {
        StuckWatcher watcher = new StuckWatcher(0.05, 2);
        watcher.observe(0, 64, 0);
        watcher.observe(0, 64, 0);

        assertFalse(
                watcher.observe(1, 64, 0),
                "возобновившееся движение сбрасывает счётчик простоя"
                        + " Иначе тест observe_MovementAfterStillness_ResetsCounter перестанет ловить эту поломку. При смене observe_MovementAfterStillness_ResetsCounter проверь это утверждение.");
        assertFalse(
                watcher.observe(1, 64, 0),
                "после сброса нужно снова набрать полный порог одинаковых тиков"
                        + " Иначе тест observe_MovementAfterStillness_ResetsCounter перестанет ловить эту поломку. При смене observe_MovementAfterStillness_ResetsCounter проверь это утверждение.");
    }

    @Test
    void constructor_NonPositiveMovement_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new StuckWatcher(0, 2),
                "Нулевой порог движения обязан отклоняться: на нём любой шум позиции считается застреванием"
                        + " Иначе тест constructor_NonPositiveMovement_IsRejected перестанет ловить эту поломку. При смене constructor_NonPositiveMovement_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("0"),
                "Отказ обязан называть порог '0', чтобы видеть, какое значение перепроверить. Фактическое: "
                        + error.getMessage()
                        + " Иначе тест constructor_NonPositiveMovement_IsRejected перестанет ловить эту поломку. При смене constructor_NonPositiveMovement_IsRejected проверь это утверждение.");
    }

    @Test
    void constructor_StuckTicksBelowOne_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new StuckWatcher(0.05, 0),
                "Порог застревания меньше единицы обязан отклоняться: детект сработал бы до наблюдения"
                        + " Иначе тест constructor_StuckTicksBelowOne_IsRejected перестанет ловить эту поломку. При смене constructor_StuckTicksBelowOne_IsRejected проверь это утверждение.");
    }
}
