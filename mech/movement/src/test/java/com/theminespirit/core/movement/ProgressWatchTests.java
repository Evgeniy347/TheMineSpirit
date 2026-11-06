package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProgressWatchTests {
    @Test
    void observe_FirstSample_IsNotStalled() {
        ProgressWatch watch = new ProgressWatch(0.5, 3);

        assertFalse(
                watch.observe(10),
                "первое наблюдение задаёт лучшую дистанцию и не может быть застреванием: иначе дух "
                        + "бросал бы цель в первый же тик");
    }

    @Test
    void observe_DistanceUnchangedForStalledTicks_ReportsStall() {
        ProgressWatch watch = new ProgressWatch(0.5, 3);
        watch.observe(10);

        assertFalse(watch.observe(10), "первый тик без прогресса ещё не порог");
        assertFalse(watch.observe(10), "второй тик без прогресса ещё не порог");
        assertTrue(
                watch.observe(10),
                "дух, три тика не приблизившийся к цели движения, застрял: иначе возврат к якорю "
                        + "висит до общего тайм-аута");
    }

    @Test
    void observe_ProgressMade_ResetsCounter() {
        ProgressWatch watch = new ProgressWatch(0.5, 2);
        watch.observe(10);
        watch.observe(10);

        assertFalse(watch.observe(9), "приближение сбрасывает счётчик простоя");
        assertFalse(watch.observe(9), "после сброса нужно снова набрать полный порог без прогресса");
    }

    @Test
    void observe_SmallJitter_IsNotProgress() {
        ProgressWatch watch = new ProgressWatch(0.5, 2);
        watch.observe(10);

        assertFalse(watch.observe(9.8), "дрожание меньше порога не считается приближением");
        assertTrue(
                watch.observe(9.8),
                "шум позиции не приближает духа к цели: застревание обязано срабатывать и на дрейфе");
    }

    @Test
    void constructor_NonPositiveProgress_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProgressWatch(0, 2),
                "нулевой порог прогресса обязан отклоняться: на нём любой шум считается движением");
    }

    @Test
    void constructor_StalledTicksBelowOne_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProgressWatch(0.5, 0),
                "порог без прогресса меньше единицы обязан отклоняться: детект сработал бы до наблюдения");
    }
}
