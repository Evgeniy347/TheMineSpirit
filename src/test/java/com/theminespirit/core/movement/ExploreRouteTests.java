package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExploreRouteTests {
    private static final BlockCoord ORIGIN = new BlockCoord(10, 64, -5);

    @Test
    void points_RadiusTwoBudgetSixteen_StartsWithOriginAndStaysInRadius() {
        int radius = 2;
        List<BlockCoord> points = ExploreRoute.points(ORIGIN, radius, 16);

        assertEquals(ORIGIN, points.get(0),
                "Маршрут разведки обязан начинаться на месте духа: точка старта — первая точка обхода");
        for (BlockCoord point : points) {
            double distance = Math.hypot(point.x() - ORIGIN.x(), point.z() - ORIGIN.z());
            assertTrue(distance <= radius + 1.0E-9,
                    "Точка маршрута " + point + " вышла за радиус " + radius + ": дух уйдёт дальше разрешённой области");
            assertEquals(ORIGIN.y(), point.y(),
                    "Точки маршрута лежат на высоте старта: высоту подбирает исполнитель по рельефу, а не ядро");
        }
        assertEquals(new HashSet<>(points).size(), points.size(),
                "Маршрут не должен повторять точки: повтор тратит бюджет на уже обследованную клетку");
    }

    @Test
    void points_RadiusTwo_AreDeterministicAndMatchSpiral() {
        List<BlockCoord> first = ExploreRoute.points(ORIGIN, 2, 16);
        List<BlockCoord> second = ExploreRoute.points(ORIGIN, 2, 16);

        assertEquals(first, second,
                "Маршрут обязан быть детерминированным: одинаковые параметры дают одинаковый обход");
        assertEquals(List.of(
                new BlockCoord(10, 64, -5),
                new BlockCoord(9, 64, -6), new BlockCoord(10, 64, -6), new BlockCoord(11, 64, -6),
                new BlockCoord(11, 64, -5), new BlockCoord(11, 64, -4), new BlockCoord(10, 64, -4),
                new BlockCoord(9, 64, -4), new BlockCoord(9, 64, -5),
                new BlockCoord(10, 64, -7), new BlockCoord(12, 64, -5), new BlockCoord(10, 64, -3),
                new BlockCoord(8, 64, -5)), first,
                "Спираль обходит кольца по часовой стрелке от старта: порядок задаёт, какие клетки попадут "
                        + "в бюджет первыми, и его нельзя менять молча");
    }

    @Test
    void points_BudgetFourRadiusEight_ReachesEdgeAndStopsAtBudget() {
        List<BlockCoord> points = ExploreRoute.points(ORIGIN, 8, 4);

        assertEquals(4, points.size(),
                "Бюджет — жёсткий предел числа точек: лишняя точка тратит серверный тик");
        double farthest = points.stream()
                .mapToDouble(point -> Math.hypot(point.x() - ORIGIN.x(), point.z() - ORIGIN.z()))
                .max().orElseThrow();
        assertTrue(farthest >= 8 - 1.0E-9,
                "Разреженный маршрут обязан доходить до края радиуса, а дальше " + farthest
                        + ": иначе бюджет 4 обошёл бы только центр");
    }

    @Test
    void points_RadiusOne_CoversCellAndFourNeighbours() {
        List<BlockCoord> points = ExploreRoute.points(ORIGIN, 1, 9);

        assertEquals(5, points.size(),
                "Радиус 1 покрывает старт и четыре соседние клетки: диагонали дальше радиуса и в область не входят");
    }
}
