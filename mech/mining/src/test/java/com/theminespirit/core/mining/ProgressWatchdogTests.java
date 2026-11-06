package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.script.CatalogLimits;
import org.junit.jupiter.api.Test;

class ProgressWatchdogTests {
    @Test
    void watchdog_TicksWithoutProgress_StallsAtLimit() {
        ProgressWatchdog watchdog = new ProgressWatchdog(3);

        watchdog.tick();
        watchdog.tick();
        assertFalse(
                watchdog.stalled(),
                "Сторож обязан молчать, пока тиков без прогресса меньше предела: ранний отказ рвёт долгую "
                        + "законную добычу на подходе к дальнему блоку");

        watchdog.tick();
        assertTrue(
                watchdog.stalled(),
                "На пределе тиков без прогресса сторож обязан объявить застой: иначе добыча висит до общего "
                        + "тайм-аута и не называет причину");
    }

    @Test
    void watchdog_ProgressResetsIdle_DoesNotStall() {
        ProgressWatchdog watchdog = new ProgressWatchdog(3);

        watchdog.tick();
        watchdog.tick();
        watchdog.tick();
        watchdog.progress();
        watchdog.tick();
        watchdog.tick();

        assertFalse(
                watchdog.stalled(),
                "Добытый блок обязан сбрасывать счётчик простоя: иначе сторож убьёт живую добычу на длинной "
                        + "серии подходов между двумя блоками");
        assertEquals(2, watchdog.idleTicks(), "После сброса простой считается заново: 2 тика, а не накопленные 5");
    }

    @Test
    void watchdog_LimitBelowOne_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProgressWatchdog(0),
                "Нулевой предел застоя обязан отвергаться: сторож сработал бы до первого наблюдения");
    }

    @Test
    void harvestNoProgressLimit_ProductionConstant_IsOneMinute() {
        assertEquals(
                1200,
                CatalogLimits.HARVEST_NO_PROGRESS_TICKS,
                "Предел простоя добычи — 1200 тиков (60 с): законный подход сокращает расстояние и сбрасывает "
                        + "счётчик, а зависший дух без нового блока обязан упасть с причиной за минуту, не за пять");
        assertEquals(
                60,
                CatalogLimits.HARVEST_NO_PROGRESS_TICKS / 20,
                "Предел — 60 секунд: числа задачи заданы в секундах, перевод в тики обязан совпадать");
    }

    @Test
    void watchdog_NoProgressLongerThanLimit_ReportsExactlyLimitTicks() {
        ProgressWatchdog watchdog = new ProgressWatchdog(CatalogLimits.HARVEST_NO_PROGRESS_TICKS);

        for (int tick = 1; tick < CatalogLimits.HARVEST_NO_PROGRESS_TICKS; tick++) {
            watchdog.tick();
            assertFalse(watchdog.stalled(), "до предела сторож обязан молчать на тике " + tick);
        }
        watchdog.tick();

        assertTrue(watchdog.stalled(), "на пределе сторож обязан объявить застой");
        assertEquals(
                CatalogLimits.HARVEST_NO_PROGRESS_TICKS,
                watchdog.idleTicks(),
                "простой обязан совпасть с пределом: иначе причина назовёт не то время");
    }
}
