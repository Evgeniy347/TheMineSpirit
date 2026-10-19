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
    void harvestNoProgressLimit_ProductionConstant_IsFiveMinutes() {
        assertEquals(
                6000,
                CatalogLimits.HARVEST_NO_PROGRESS_TICKS,
                "Предел простоя добычи — 5 минут: он обязан перекрывать самый долгий законный подход "
                        + "(радиус 32 ≈ 2400 тиков) и при этом ловить зависание до общего тайм-аута задачи");
        assertEquals(
                300,
                CatalogLimits.HARVEST_NO_PROGRESS_TICKS / 20,
                "Предел — 300 секунд: числа задачи заданы в секундах, перевод в тики обязан совпадать");
    }
}
