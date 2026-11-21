package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StallWatchTests {
    @Test
    void observe_BackAndForthNearStep_ReportsStall() {
        StallWatch watch = new StallWatch(40);
        boolean stalled = false;
        for (int tick = 0; tick < 120 && !stalled; tick++) {
            double z = -488.5 + ((tick % 4) < 2 ? 0.3 : -0.3);
            double y = tick % 4;
            stalled = watch.observe(-168.5 + ((tick % 4) < 2 ? 0.2 : -0.2), z + y * 0.001);
        }

        assertTrue(
                stalled,
                "Топтание у ступени — движение с возвратом: дух колеблется в пределах полублока и возвращается "
                        + "в точку (окно 43, spirit-3 у -168.5,69,-488.5, путь 6.41 при смещении 0.04, прыжки "
                        + "69↔70). Иначе StuckWatcher не видит топтание (шаг больше 0.05 за тик) и дух толчётся "
                        + "секундами. При смене StallWatch проверь прямую ходьбу: она не должна считаться "
                        + "топтанием");
    }

    @Test
    void observe_StraightWalk_IsNeverStall() {
        StallWatch watch = new StallWatch(40);
        boolean stalled = false;
        for (int tick = 0; tick < 200 && !stalled; tick++) {
            stalled = watch.observe(tick * 0.1, 0);
        }

        assertFalse(
                stalled,
                "Прямая ходьба — прогресс, а не топтание: смещение за окно растёт вместе с путём. Иначе дух получал "
                        + "бы ложный отказ подхода на ровной дороге. При смене StallWatch проверь живой проход "
                        + "по поселению: прямое движение без отказа");
    }

    @Test
    void observe_StandingStill_IsNotStall() {
        StallWatch watch = new StallWatch(40);
        boolean stalled = false;
        for (int tick = 0; tick < 200 && !stalled; tick++) {
            stalled = watch.observe(10, 10);
        }

        assertFalse(
                stalled,
                "Стояние без движения — забота StuckWatcher, а не топтания: StallWatch требует пройденного "
                        + "пути, иначе любой простой духа объявлялся бы отвергнутым подходом. При смене "
                        + "StallWatch проверь стоящего духа: топтания нет, отказ даёт StuckWatcher");
    }

    @Test
    void observe_VerticalClimb_IsNotStall() {
        StallWatch watch = new StallWatch(20);
        boolean stalled = false;
        for (int tick = 0; tick < 200 && !stalled; tick++) {
            stalled = watch.observe(0.5, 0.5);
        }

        assertFalse(
                stalled,
                "Подъём по лестнице без горизонтального смещения — не топтание: путь по горизонтали ноль. Иначе "
                        + "ложный отказ сбросил бы рабочего духа с лестницы шахты. При смене StallWatch проверь "
                        + "живой подъём по лестнице: движения вперёд нет, отказа нет");
    }
}
