package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.route.RouteCell;
import com.theminespirit.core.route.RouteWorld;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SurfaceEscapeSessionTests {
    private static final long NANOS_PER_QUERY = 10_000;
    private static final BlockCoord START = new BlockCoord(0, 1, 0);
    private static final int BUDGET_MILLIS = 5;

    @Test
    void step_HeavySearch_StopsWithinBudgetAndContinuesNextStep() {
        BlockCoord goal = new BlockCoord(200, 1, 0);
        CountingWorld world = new CountingWorld(1);
        SurfaceEscapeSession session =
                new SurfaceEscapeSession(START, world, goal::equals, goal, cell -> false, null, world::nanos);

        long maxStep = 0;
        int steps = 0;
        SurfaceEscapeSession.Result result = null;
        while (result == null) {
            long before = world.nanos();
            result = session.step(BUDGET_MILLIS * 1_000_000L);
            maxStep = Math.max(maxStep, world.nanos() - before);
            steps++;
            assertTrue(
                    steps < 10_000,
                    "Поиск обязан закончиться: иначе шаг бюджета не двигает работу"
                            + " При смене step_HeavySearch_StopsWithinBudgetAndContinuesNextStep проверь это утверждение.");
        }

        assertTrue(
                steps > 1,
                "Долгий поиск не укладывается в один тик: работа делится на " + steps + " шагов"
                        + " Иначе тест step_HeavySearch_StopsWithinBudgetAndContinuesNextStep перестанет ловить эту поломку. При смене step_HeavySearch_StopsWithinBudgetAndContinuesNextStep проверь это утверждение.");
        assertTrue(
                maxStep <= (BUDGET_MILLIS + 1) * 1_000_000L,
                "Один шаг держал поток " + maxStep / 1_000_000L + " мс при бюджете " + BUDGET_MILLIS
                        + " мс: путь на 200 клеток не должен вставать в тик целиком"
                        + " Иначе тест step_HeavySearch_StopsWithinBudgetAndContinuesNextStep перестанет ловить эту поломку. При смене step_HeavySearch_StopsWithinBudgetAndContinuesNextStep проверь это утверждение.");
        assertTrue(
                result.found(),
                "Цель на плоскости достижима — путь обязан найтись"
                        + " Иначе тест step_HeavySearch_StopsWithinBudgetAndContinuesNextStep перестанет ловить эту поломку. При смене step_HeavySearch_StopsWithinBudgetAndContinuesNextStep проверь это утверждение.");
        assertEquals(
                goal,
                result.path().get(result.path().size() - 1),
                "Поиск порциями обязан прийти в ту же цель, что и синхронный"
                        + " Иначе тест step_HeavySearch_StopsWithinBudgetAndContinuesNextStep перестанет ловить эту поломку. При смене step_HeavySearch_StopsWithinBudgetAndContinuesNextStep проверь это утверждение.");
    }

    @Test
    void step_ZeroBudget_DoesNoWorkAndWaitsNextTick() {
        CountingWorld world = new CountingWorld(1);
        BlockCoord goal = new BlockCoord(60, 1, 0);
        SurfaceEscapeSession session =
                new SurfaceEscapeSession(START, world, goal::equals, goal, cell -> false, null, world::nanos);

        SurfaceEscapeSession.Result result = session.step(0);

        assertNull(
                result,
                "Нулевой бюджет не ищет: поиск продолжится на следующем тике"
                        + " Иначе тест step_ZeroBudget_DoesNoWorkAndWaitsNextTick перестанет ловить эту поломку. При смене step_ZeroBudget_DoesNoWorkAndWaitsNextTick проверь это утверждение.");
    }

    @Test
    void step_SameWorld_ReturnsSamePathAsSynchronousSearch() {
        BlockCoord goal = new BlockCoord(40, 1, 25);
        CountingWorld world = new CountingWorld(1);
        SurfaceEscapeSession session =
                new SurfaceEscapeSession(START, world, goal::equals, goal, cell -> false, null, world::nanos);

        SurfaceEscapeSession.Result result = null;
        while (result == null) {
            result = session.step(1_000_000L);
        }
        Optional<List<BlockCoord>> direct = SurfaceEscape.path(START, world, goal::equals, goal);

        assertTrue(
                direct.isPresent(),
                "Предусловие: синхронный поиск находит цель"
                        + " Иначе тест step_SameWorld_ReturnsSamePathAsSynchronousSearch перестанет ловить эту поломку. При смене step_SameWorld_ReturnsSamePathAsSynchronousSearch проверь это утверждение.");
        assertEquals(
                direct.orElseThrow(),
                result.path(),
                "Порционный поиск — тот же путь, а не другой обход"
                        + " Иначе тест step_SameWorld_ReturnsSamePathAsSynchronousSearch перестанет ловить эту поломку. При смене step_SameWorld_ReturnsSamePathAsSynchronousSearch проверь это утверждение.");
    }

    @Test
    void toHome_HomeReachable_PrefersHomeOverSky() {
        BlockCoord home = new BlockCoord(-30, 1, 0);
        BlockCoord sky = new BlockCoord(30, 1, 0);
        CountingWorld world = new CountingWorld(1);
        SurfaceEscapeSession session = new SurfaceEscapeSession(
                START, world, cell -> cell.equals(home), home, cell -> false, cell -> cell.equals(sky), world::nanos);

        SurfaceEscapeSession.Result result = null;
        while (result == null) {
            result = session.step(1_000_000L);
        }

        assertTrue(
                result.found(),
                "Дом достижим — путь к дому обязан найтись"
                        + " Иначе тест toHome_HomeReachable_PrefersHomeOverSky перестанет ловить эту поломку. При смене toHome_HomeReachable_PrefersHomeOverSky проверь это утверждение.");
        assertTrue(
                result.primary(),
                "Дом — основная цель: запасное небо не подменяет его, даже если ближе"
                        + " Иначе тест toHome_HomeReachable_PrefersHomeOverSky перестанет ловить эту поломку. При смене toHome_HomeReachable_PrefersHomeOverSky проверь это утверждение.");
        assertEquals(
                home,
                result.path().get(result.path().size() - 1),
                "Конец пути — дом, а не любое небо"
                        + " Иначе тест toHome_HomeReachable_PrefersHomeOverSky перестанет ловить эту поломку. При смене toHome_HomeReachable_PrefersHomeOverSky проверь это утверждение.");
    }

    @Test
    void toHome_HomeUnreachable_FallsBackToSky() {
        BlockCoord home = new BlockCoord(-30, 1, 0);
        BlockCoord sky = new BlockCoord(30, 1, 0);
        CountingWorld world = new CountingWorld(1);
        world.seal(home);
        SurfaceEscapeSession session = new SurfaceEscapeSession(
                START, world, cell -> cell.equals(home), home, cell -> false, cell -> cell.equals(sky), world::nanos);

        SurfaceEscapeSession.Result result = null;
        while (result == null) {
            result = session.step(1_000_000L);
        }

        assertTrue(
                result.found(),
                "Дом замурован, но небо достижимо: спасение обязано состояться"
                        + " Иначе тест toHome_HomeUnreachable_FallsBackToSky перестанет ловить эту поломку. При смене toHome_HomeUnreachable_FallsBackToSky проверь это утверждение.");
        assertFalse(
                result.primary(),
                "Найден запасной выход, а не дом — журнал обязан назвать это честно"
                        + " Иначе тест toHome_HomeUnreachable_FallsBackToSky перестанет ловить эту поломку. При смене toHome_HomeUnreachable_FallsBackToSky проверь это утверждение.");
        assertEquals(
                sky,
                result.path().get(result.path().size() - 1),
                "Конец пути — запасная клетка неба"
                        + " Иначе тест toHome_HomeUnreachable_FallsBackToSky перестанет ловить эту поломку. При смене toHome_HomeUnreachable_FallsBackToSky проверь это утверждение.");
    }

    @Test
    void step_NoExitAtAll_EndsEmpty() {
        CountingWorld world = new CountingWorld(1);
        world.fill(-2, 0, -2, 2, 3, 2, RouteCell.solid(15));
        world.fill(-1, 1, -1, 1, 2, 1, RouteCell.AIR);
        SurfaceEscapeSession session = new SurfaceEscapeSession(
                new BlockCoord(0, 1, 0), world, cell -> false, null, cell -> false, null, world::nanos);

        SurfaceEscapeSession.Result result = null;
        while (result == null) {
            result = session.step(1_000_000L);
        }

        assertFalse(
                result.found(),
                "Выхода нет — путь не выдумывается"
                        + " Иначе тест step_NoExitAtAll_EndsEmpty перестанет ловить эту поломку. При смене step_NoExitAtAll_EndsEmpty проверь это утверждение.");
        assertTrue(
                result.visits() <= SurfaceEscapeSession.MAX_VISITS,
                "Предел посещений держится"
                        + " Иначе тест step_NoExitAtAll_EndsEmpty перестанет ловить эту поломку. При смене step_NoExitAtAll_EndsEmpty проверь это утверждение.");
    }

    private static final class CountingWorld implements RouteWorld {
        private final Map<BlockCoord, Integer> cells = new HashMap<>();
        private final int ground;
        private long nanos;

        private CountingWorld(int ground) {
            this.ground = ground;
        }

        long nanos() {
            return nanos;
        }

        void fill(int x0, int y0, int z0, int x1, int y1, int z1, int cell) {
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
                for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                    for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                        cells.put(new BlockCoord(x, y, z), cell);
                    }
                }
            }
        }

        void seal(BlockCoord center) {
            fill(
                    center.x() - 2,
                    center.y() - 2,
                    center.z() - 2,
                    center.x() + 2,
                    center.y() + 2,
                    center.z() + 2,
                    RouteCell.solid(15));
            cells.put(center, RouteCell.AIR);
            cells.put(new BlockCoord(center.x(), center.y() + 1, center.z()), RouteCell.AIR);
        }

        @Override
        public int cell(int x, int y, int z) {
            nanos += NANOS_PER_QUERY;
            Integer cell = cells.get(new BlockCoord(x, y, z));
            if (cell != null) {
                return cell;
            }
            return y < ground ? RouteCell.solid(15) : RouteCell.AIR;
        }
    }
}
