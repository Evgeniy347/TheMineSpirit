package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class WanderPolicyTests {
    private static final BlockCoord ORIGIN = new BlockCoord(100, 64, -40);

    @Test
    void next_SameSeed_SamePoint() {
        assertEquals(WanderPolicy.next(7, ORIGIN, 8, 0), WanderPolicy.next(7, ORIGIN, 8, 0),
                "Повтор с тем же сидом обязан давать ту же точку: иначе блуждание нетестируемо "
                        + "и перезагрузка духа меняет маршрут");
    }

    @Test
    void next_StuckAttempt_PicksNewPoint() {
        BlockCoord first = WanderPolicy.next(7, ORIGIN, 8, 0);
        BlockCoord retry = WanderPolicy.next(7, ORIGIN, 8, 1);

        assertTrue(!first.equals(retry),
                "Новая попытка после застревания обязана давать другую точку: иначе дух стоит на месте "
                        + "вместо выбора нового направления");
    }

    @Test
    void next_PointWithinRadius_LiesInsideSquare() {
        BlockCoord point = WanderPolicy.next(7, ORIGIN, 8, 0);

        assertTrue(Math.abs(point.x() - ORIGIN.x()) <= 8 && Math.abs(point.z() - ORIGIN.z()) <= 8,
                "Точка блуждания обязана лежать в квадрате радиуса от старта: иначе дух уходит за пределы "
                        + "порученной зоны. Точка: " + point);
        assertTrue(Math.abs(point.y() - ORIGIN.y()) <= WanderPolicy.VERTICAL_SPREAD,
                "Точка блуждания обязана лежать в пределах ±2 по высоте от старта: иначе дух пытается "
                        + "взлететь или закопаться. Точка: " + point);
    }

    @Test
    void next_ZeroRadius_ReturnsOrigin() {
        assertEquals(ORIGIN, WanderPolicy.next(7, ORIGIN, 0, 0),
                "Нулевой радиус обязан возвращать старт: шагать некуда, дух остаётся на месте");
    }

    @Test
    void next_NegativeRadius_Throws() {
        assertThrows(IllegalArgumentException.class, () -> WanderPolicy.next(7, ORIGIN, -1, 0),
                "Отрицательный радиус обязан отвергаться: точка с таким радиусом недостижима");
    }

    @Test
    void next_NegativeAttempt_Throws() {
        assertThrows(IllegalArgumentException.class, () -> WanderPolicy.next(7, ORIGIN, 8, -1),
                "Отрицательная попытка обязана отвергаться: счётчик попыток растёт от нуля при застревании");
    }

    @Test
    void verticalSpread_ValueCheck_EqualsTwo() {
        assertEquals(2, WanderPolicy.VERTICAL_SPREAD,
                "Разброс блуждания по высоте — ±2 блока: больше — дух пытается взлететь или закопаться, "
                        + "при осознанной смене сверьте скрипт блуждания");
    }
}
