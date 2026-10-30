package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaskStallTests {
    private static final UUID SPIRIT = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final BlockCoord BOTTOM = new BlockCoord(-163, 17, -432);

    @Test
    void stalled_NoScriptSameSpot_AfterLimit() {
        TaskStall stall = new TaskStall();
        stall.stalled(SPIRIT, null, BOTTOM, 0);

        assertFalse(stall.stalled(SPIRIT, null, BOTTOM, TaskStall.STALL_TICKS - 1), "До предела — ещё не простой");
        assertTrue(
                stall.stalled(SPIRIT, null, BOTTOM, TaskStall.STALL_TICKS),
                "Задание доски без скрипта на одном месте минуту — простой: «busy» без задачи 5 минут (INC-65)");
    }

    @Test
    void stalled_GotoMoving_NotStalled() {
        TaskStall stall = new TaskStall();
        for (int step = 0; step <= 3; step++) {
            assertFalse(
                    stall.stalled(SPIRIT, "goto", new BlockCoord(step * 5, 64, 0), step * TaskStall.STALL_TICKS),
                    "Подход, который идёт, — не простой");
        }
    }

    @Test
    void stalled_LongSmeltOnSpot_NotStalled() {
        TaskStall stall = new TaskStall();
        stall.stalled(SPIRIT, "smelt", BOTTOM, 0);

        assertFalse(
                stall.stalled(SPIRIT, "smelt", BOTTOM, 10 * TaskStall.STALL_TICKS),
                "Плавка на месте — работа со своим скриптом, не простой");
    }

    @Test
    void noProgress_WalkingSameProgress_AfterLimit() {
        TaskStall stall = new TaskStall();
        stall.noProgress(SPIRIT, "1/clear_area/0", 0);

        assertFalse(stall.noProgress(SPIRIT, "1/clear_area/0", TaskStall.NO_PROGRESS_TICKS - 1), "До предела — нет");
        assertTrue(
                stall.noProgress(SPIRIT, "1/clear_area/0", TaskStall.NO_PROGRESS_TICKS),
                "Дух ходит, но прогресс задачи не меняется — простой: 10 минут по пещере без срезки (INC-67)");
        assertFalse(stall.noProgress(SPIRIT, "1/clear_area/1", TaskStall.NO_PROGRESS_TICKS + 1), "Сдвиг — сброс");
    }

    @Test
    void noProgress_PausedByPolicy_RestartsClockInsteadOfFailing() {
        TaskStall stall = new TaskStall();
        stall.noProgress(SPIRIT, "2/chop_tree/1", 0);

        assertFalse(
                stall.noProgress(SPIRIT, "2/chop_tree/1", TaskStall.NO_PROGRESS_TICKS, true),
                "Пауза политики (AIR: голова под водой) — не простой задачи: рефлекс выводит на сушу, "
                        + "а задание продолжается, а не проваливается через 3600 тиков (наблюдение 7)");
        assertFalse(
                stall.noProgress(SPIRIT, "2/chop_tree/1", TaskStall.NO_PROGRESS_TICKS + 1),
                "Выход из паузы начинает отсчёт заново: время под водой не съедает бюджет ожидания");
    }

    @Test
    void stalled_PausedByPolicy_DoesNotFail() {
        TaskStall stall = new TaskStall();
        stall.stalled(SPIRIT, null, BOTTOM, 0);

        assertFalse(
                stall.stalled(SPIRIT, null, BOTTOM, TaskStall.STALL_TICKS, true),
                "Стоянка на месте под паузой политики — не простой задания: дух не виноват, что его "
                        + "держит воздух/вода");
    }
}
