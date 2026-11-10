package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class GoalTests {
    private static final double EPS = 1e-9;

    record GoalRow(Goal goal, BlockCoord feet, boolean satisfied, double heuristic) {
        @Override
        public String toString() {
            return goal + " от " + feet;
        }
    }

    static Stream<Arguments> goalTable() {
        return Stream.of(
                Arguments.of(new GoalRow(new GoalNear(new BlockCoord(0, 0, 0), 2), new BlockCoord(0, 0, 0), true, 0.0)),
                Arguments.of(new GoalRow(new GoalNear(new BlockCoord(0, 0, 0), 2), new BlockCoord(2, 0, 0), true, 0.0)),
                Arguments.of(
                        new GoalRow(new GoalNear(new BlockCoord(0, 0, 0), 2), new BlockCoord(5, 0, 0), false, 3.0)),
                Arguments.of(new GoalRow(new GoalNear(new BlockCoord(1, 1, 1), 1), new BlockCoord(1, 2, 1), true, 0.0)),
                Arguments.of(new GoalRow(
                        new GoalComposite(List.of(
                                new GoalNear(new BlockCoord(0, 0, 0), 0), new GoalNear(new BlockCoord(10, 0, 0), 0))),
                        new BlockCoord(10, 0, 0),
                        true,
                        0.0)),
                Arguments.of(new GoalRow(
                        new GoalComposite(List.of(
                                new GoalNear(new BlockCoord(0, 0, 0), 0), new GoalNear(new BlockCoord(10, 0, 0), 0))),
                        new BlockCoord(5, 0, 0),
                        false,
                        5.0)),
                Arguments.of(new GoalRow(
                        new GoalComposite(List.of(
                                new GoalNear(new BlockCoord(0, 0, 0), 0), new GoalNear(new BlockCoord(3, 0, 4), 0))),
                        new BlockCoord(3, 0, 4),
                        true,
                        0.0)),
                Arguments.of(new GoalRow(
                        new GoalComposite(List.of(new GoalNear(new BlockCoord(7, 0, 0), 0))),
                        new BlockCoord(4, 0, 0),
                        false,
                        3.0)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("goalTable")
    void goal_TableRow_SatisfactionAndHeuristicMatch(GoalRow row) {
        assertEquals(
                row.satisfied(),
                row.goal().isSatisfied(row.feet()),
                "Цель " + row.goal() + " обязана считать позицию " + row.feet()
                        + (row.satisfied() ? " достигнутой" : " недостигнутой")
                        + ": иначе дух либо остановится раньше цели, либо пройдёт мимо неё");
        assertEquals(
                row.heuristic(),
                row.goal().heuristic(row.feet()),
                EPS,
                "Эвристика цели " + row.goal() + " от " + row.feet() + " обязана равняться " + row.heuristic()
                        + ": неверная эвристика ломает порядок целей и допустимость поиска пути");
    }

    @Test
    void composite_EmptyList_Throws() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new GoalComposite(List.of()),
                "Составная цель без вложенных обязана отвергаться: пустая цель недостижима, "
                        + "и дух встанет до первого шага, а стратег не узнает почему");

        assertTrue(
                error.getMessage().contains("пуст"),
                "Отказ обязан объяснять, что список целей пуст. Фактическое: " + error.getMessage());
    }

    @Test
    void near_NegativeRadius_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new GoalNear(new BlockCoord(0, 0, 0), -1),
                "Окрестность с отрицательным радиусом обязана отвергаться: такая цель недостижима "
                        + "и дух встанет до первого шага");
    }
}
