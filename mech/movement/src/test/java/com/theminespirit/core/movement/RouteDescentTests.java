package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RouteDescentTests {
    @Test
    void surfaceGoal_PathDropsIntoCave_Refused() {
        String refusal = RouteDescent.refusal(70, 70, List.of(70, 60, 47, 47));

        assertTrue(
                refusal != null && refusal.contains("47"),
                "Маршрут к поверхности не уходит на 23 блока вниз: это пещера, откуда дух не выйдет (observe24 B1): "
                        + refusal);
    }

    @Test
    void surfaceGoal_PathStaysOnSurface_Allowed() {
        assertNull(
                RouteDescent.refusal(70, 70, List.of(70, 69, 70, 71)),
                "Обычный рельеф с подъёмом и спуском на блок не запрещён");
    }

    @Test
    void lowerGoal_DescentToIt_Allowed() {
        assertNull(
                RouteDescent.refusal(70, 40, List.of(70, 50, 40)),
                "Спуск к цели, которая сама ниже, — работа, а не ловушка: маршрут разрешён");
    }

    @Test
    void deepStart_PathGoesUp_Allowed() {
        assertNull(
                RouteDescent.refusal(27, 70, List.of(27, 30, 45, 70)),
                "Дух уже в яме и идёт вверх: маршрут спасения не режется по глубине старта");
    }

    @Test
    void surfaceGoal_PathDropsTenBlocks_Refused() {
        String refusal = RouteDescent.refusal(70, 70, List.of(70, 60));

        assertTrue(
                refusal != null,
                "Даже 10 блоков ниже цели — уже ловушка: предел " + RouteDescent.MAX_BELOW + " блоков");
    }
}
