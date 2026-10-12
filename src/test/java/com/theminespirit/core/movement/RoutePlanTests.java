package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RoutePlanTests {
    record RouteRow(RoutePlan.Mode mode, int size, int current, Set<Integer> skipped, Optional<Integer> next) {
        @Override
        public String toString() {
            return mode + " из " + current + " мимо " + skipped;
        }
    }

    static Stream<Arguments> routeTable() {
        return Stream.of(
                Arguments.of(new RouteRow(RoutePlan.Mode.LOOP, 4, 0, Set.of(), Optional.of(1))),
                Arguments.of(new RouteRow(RoutePlan.Mode.LOOP, 4, 3, Set.of(), Optional.of(0))),
                Arguments.of(new RouteRow(RoutePlan.Mode.LOOP, 4, 1, Set.of(2), Optional.of(3))),
                Arguments.of(new RouteRow(RoutePlan.Mode.LOOP, 4, 2, Set.of(0, 1, 3), Optional.of(2))),
                Arguments.of(new RouteRow(RoutePlan.Mode.LOOP, 4, 0, Set.of(1, 2, 3), Optional.of(0))),
                Arguments.of(new RouteRow(RoutePlan.Mode.LOOP, 1, 0, Set.of(), Optional.of(0))),
                Arguments.of(new RouteRow(RoutePlan.Mode.LOOP, 3, 0, Set.of(0, 1, 2), Optional.empty())),
                Arguments.of(new RouteRow(RoutePlan.Mode.PING_PONG, 4, 0, Set.of(), Optional.of(1))),
                Arguments.of(new RouteRow(RoutePlan.Mode.PING_PONG, 4, 3, Set.of(), Optional.of(2))),
                Arguments.of(new RouteRow(RoutePlan.Mode.PING_PONG, 4, 1, Set.of(2), Optional.of(3))),
                Arguments.of(new RouteRow(RoutePlan.Mode.PING_PONG, 4, 2, Set.of(3), Optional.of(1))),
                Arguments.of(new RouteRow(RoutePlan.Mode.PING_PONG, 4, 1, Set.of(0, 2, 3), Optional.of(1))),
                Arguments.of(new RouteRow(RoutePlan.Mode.PING_PONG, 2, 0, Set.of(), Optional.of(1))),
                Arguments.of(new RouteRow(RoutePlan.Mode.PING_PONG, 2, 1, Set.of(), Optional.of(0))),
                Arguments.of(new RouteRow(RoutePlan.Mode.PING_PONG, 3, 1, Set.of(0, 1, 2), Optional.empty())));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("routeTable")
    void next_TableRow_NextPointMatches(RouteRow row) {
        RoutePlan plan = plan(row.size(), row.mode());

        assertEquals(row.next(), plan.next(row.current(), row.skipped()),
                "Маршрут " + row.mode() + " из точки " + row.current() + " мимо " + row.skipped()
                        + " обязан вести в " + row.next() + ": неверный порядок обегает точки дважды "
                        + "или виснет на недостижимой");
    }

    @Test
    void next_LoopFullCircle_VisitsAllInOrder() {
        RoutePlan plan = plan(4, RoutePlan.Mode.LOOP);

        List<Integer> visited = List.of(plan.next(0, Set.of()).orElseThrow(), plan.next(1, Set.of()).orElseThrow(),
                plan.next(2, Set.of()).orElseThrow(), plan.next(3, Set.of()).orElseThrow());

        assertEquals(List.of(1, 2, 3, 0), visited,
                "Круг из 4 точек обязан возвращаться к первой: иначе патруль ходит только по части маршрута");
    }

    @Test
    void next_PingPongFullCycle_TurnsAtEnds() {
        RoutePlan plan = plan(4, RoutePlan.Mode.PING_PONG);

        List<Integer> visited = new ArrayList<>();
        int current = 0;
        for (int i = 0; i < 6; i++) {
            current = plan.next(current, Set.of()).orElseThrow();
            visited.add(current);
        }

        assertEquals(List.of(1, 2, 3, 2, 1, 0), visited,
                "Пинг-понг обязан разворачиваться на концах 0 и 3: иначе патруль ходит кругом вместо туда-обратно");
    }

    @Test
    void constructor_EmptyRoute_Throws() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new RoutePlan(List.of(), RoutePlan.Mode.LOOP),
                "Пустой маршрут обязан отвергаться: патрулю без точек некуда идти, а стратег не узнает почему");

        assertTrue(error.getMessage().contains("без точек"),
                "Отказ обязан объяснять, что в маршруте нет точек. Фактическое: " + error.getMessage());
    }

    @Test
    void next_CurrentOutsideRoute_Throws() {
        RoutePlan plan = plan(3, RoutePlan.Mode.LOOP);

        assertThrows(IndexOutOfBoundsException.class, () -> plan.next(3, Set.of()),
                "Текущая точка вне маршрута обязана отвергаться: счётчик маршрута сбился и патруль "
                        + "стоит не на своей точке");
    }

    private static RoutePlan plan(int size, RoutePlan.Mode mode) {
        List<BlockCoord> points = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            points.add(new BlockCoord(i * 4, 0, 0));
        }
        return new RoutePlan(points, mode);
    }
}
