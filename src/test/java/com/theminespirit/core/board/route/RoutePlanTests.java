package com.theminespirit.core.board.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class RoutePlanTests {
    private static final BlockCoord HOME = new BlockCoord(0, 64, 0);

    @Test
    void mark_PointThenPathThenRoute_KeepsOrder() {
        RoutePlan plan = RoutePlan.empty().mark(RouteResource.SAND, new BlockCoord(150, 70, 20));

        assertFalse(plan.pathDone(RouteResource.SAND), "До тропы маршрут не готов: дух не дойдёт до места добычи");
        assertFalse(plan.routeDone(RouteResource.SAND), "Без тропы точка добычи не ставится раньше времени");

        plan = plan.pathBuilt(RouteResource.SAND);
        assertTrue(plan.pathDone(RouteResource.SAND), "Тропа проложена — точка добычи становится следующей");
        assertFalse(plan.routeDone(RouteResource.SAND), "Точка добычи ждёт своей очереди после тропы");

        plan = plan.routeBuilt(RouteResource.SAND);
        assertTrue(
                plan.pathDone(RouteResource.SAND) && plan.routeDone(RouteResource.SAND),
                "Тропа и точка готовы — дальше идут задания пополнения");
    }

    @Test
    void retarget_NewPoint_InvalidatesPath() {
        RoutePlan plan = RoutePlan.empty()
                .mark(RouteResource.SAND, new BlockCoord(100, 70, 0))
                .pathBuilt(RouteResource.SAND)
                .routeBuilt(RouteResource.SAND);

        RoutePlan moved = plan.retarget(RouteResource.SAND, new BlockCoord(200, 70, 100));

        assertEquals(
                new BlockCoord(200, 70, 100),
                moved.point(RouteResource.SAND).orElseThrow(),
                "Перенос маршрута ставит точку в новое место");
        assertFalse(moved.pathDone(RouteResource.SAND), "Перенос точки отменяет старую тропу: она ведёт не туда");
        assertFalse(moved.routeDone(RouteResource.SAND), "Точка добычи без новой тропы не готова");
    }

    @Test
    void mark_SamePoint_KeepsProgress() {
        RoutePlan plan = RoutePlan.empty()
                .mark(RouteResource.SNOW, new BlockCoord(0, 64, 40))
                .pathBuilt(RouteResource.SNOW);

        assertEquals(
                plan,
                plan.mark(RouteResource.SNOW, new BlockCoord(0, 64, 40)),
                "Повторная постановка в ту же точку не сбрасывает проложенную тропу");
    }

    @Test
    void marker_RouteWithoutPath_Rejected() {
        boolean rejected;
        try {
            new RouteMarker(RouteResource.DIRT, HOME, false, true);
            rejected = false;
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        assertTrue(rejected, "Готовая точка добычи без тропы — ложное состояние: порядок держит конструктор маркера");
    }
}
