package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.route.Route;
import com.theminespirit.core.route.RouteCell;
import com.theminespirit.core.route.RoutePlanner;
import com.theminespirit.core.route.RouteRules;
import com.theminespirit.core.route.RouteWorld;
import org.junit.jupiter.api.Test;

class ApproachRouteTests {
    private static final RouteRules RULES = RouteRules.load();

    private static final RouteWorld FLOOR = (x, y, z) -> y < 1 ? RouteCell.solid(1) : RouteCell.AIR;

    private static final SafetyView SAFETY = cell -> cell.y() < 1 ? Cell.SOLID : Cell.PASSABLE;

    @Test
    void plan_StartInsideGoalRadius_FindsNeighbouringStandInsteadOfEmptyRoute() {
        BlockCoord start = new BlockCoord(0, 1, 0);
        BlockCoord target = new BlockCoord(0, 0, 0);

        Route direct = RoutePlanner.plan(FLOOR, start, new GoalNear(target, 1), RULES, 0);

        assertFalse(
                direct.usable(),
                "Нулевой маршрут в ту же клетку непригоден: цель уже в радиусе, шагов нет, дух стоит на месте; "
                        + "фактически " + direct.status() + " шагов "
                        + direct.steps().size());

        Route route = ApproachRoute.plan(FLOOR, start, target, 1, RULES, 0, SAFETY);

        assertTrue(
                route.usable(),
                "Подход к цели, уже попавшей в радиус, обязан искать соседнюю стоянку: иначе дух стоит на месте "
                        + "и получает «нет прогресса 3600 тиков» (аномалия 3: пробка у калитки ограды); фактически "
                        + route.status() + " шагов " + route.steps().size());
        assertNotEquals(
                start,
                route.end(),
                "Маршрут нулевой цены в ту же клетку отброшен: конец пути — соседняя стоянка, а не место духа");
    }

    @Test
    void plan_CurrentCellIsCheapestStand_PicksAnotherStand() {
        BlockCoord start = new BlockCoord(1, 1, 0);
        BlockCoord target = new BlockCoord(0, 0, 0);

        Route route = ApproachRoute.plan(FLOOR, start, target, 1, RULES, 0, SAFETY);

        assertTrue(
                route.usable(),
                "Текущая клетка — сама по себе стоянка у цели, но маршрут в неё нулевой: нужна другая стоянка, "
                        + "иначе дух остаётся на месте");
        assertNotEquals(
                start,
                route.end(),
                "Соседняя стоянка выбирается вместо текущей: иначе маршрут снова ведёт в ту же клетку");
    }

    @Test
    void plan_DirectStepExists_KeepsDirectRoute() {
        BlockCoord start = new BlockCoord(0, 1, 4);
        BlockCoord target = new BlockCoord(0, 0, 0);

        Route direct = RoutePlanner.plan(FLOOR, start, new GoalNear(target, 1), RULES, 0);
        Route route = ApproachRoute.plan(FLOOR, start, target, 1, RULES, 0, SAFETY);

        assertTrue(direct.usable(), "Прямой подход к цели обязан иметь шаги: дух ещё не в радиусе цели");
        assertEquals(
                direct.steps().size(),
                route.steps().size(),
                "Рабочий прямой маршрут не заменяется обходной стоянкой: соседняя стоянка нужна только вместо "
                        + "нулевого маршрута");
    }
}
