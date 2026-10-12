package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MoveCostTests {
    record CostRow(MoveKind move, int height, int ticks) {
        @Override
        public String toString() {
            return move + " высота " + height;
        }
    }

    record FallRow(int height, int damage) {
        @Override
        public String toString() {
            return "падение " + height;
        }
    }

    static Stream<Arguments> costTable() {
        return Stream.of(
                Arguments.of(new CostRow(MoveKind.WALK, 0, 5)),
                Arguments.of(new CostRow(MoveKind.SPRINT, 0, 4)),
                Arguments.of(new CostRow(MoveKind.SNEAK, 0, 15)),
                Arguments.of(new CostRow(MoveKind.WATER, 0, 10)),
                Arguments.of(new CostRow(MoveKind.LADDER_UP, 0, 8)),
                Arguments.of(new CostRow(MoveKind.LADDER_DOWN, 0, 4)),
                Arguments.of(new CostRow(MoveKind.JUMP, 1, 8)),
                Arguments.of(new CostRow(MoveKind.FALL, 0, 8)),
                Arguments.of(new CostRow(MoveKind.FALL, 1, 10)),
                Arguments.of(new CostRow(MoveKind.FALL, 2, 12)),
                Arguments.of(new CostRow(MoveKind.FALL, 3, 14)),
                Arguments.of(new CostRow(MoveKind.FALL, 10, 28)));
    }

    static Stream<Arguments> fallTable() {
        return Stream.of(
                Arguments.of(new FallRow(0, 0)),
                Arguments.of(new FallRow(1, 0)),
                Arguments.of(new FallRow(2, 0)),
                Arguments.of(new FallRow(3, 1)),
                Arguments.of(new FallRow(4, 2)),
                Arguments.of(new FallRow(10, 8)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("costTable")
    void costOf_TableRow_TicksMatch(CostRow row) {
        assertEquals(row.ticks(), MoveCost.costOf(row.move(), row.height()),
                "Стоимость " + row.move() + " обязана равняться " + row.ticks() + " тиков: по ней упорядочиваются "
                        + "цели и решается «ломать или обходить», неверный тариф ведёт духа длинной дорогой");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fallTable")
    void fallDamage_TableRow_DamageMatches(FallRow row) {
        assertEquals(row.damage(), MoveCost.fallDamage(row.height()),
                "Урон падения с " + row.height() + " обязан равняться " + row.damage()
                        + ": падение на 2 безопасно, на 3 уже ранит, иначе walk_careful пустит духа с обрыва");
    }

    @Test
    void costOf_WaterVsWalk_WaterCostsMore() {
        assertEquals(true, MoveCost.costOf(MoveKind.WATER, 0) > MoveCost.costOf(MoveKind.WALK, 0),
                "Вода обязана стоить дороже шага: иначе планировщик поведёт духа вплавь вместо обхода по берегу");
    }

    @Test
    void costOf_SprintVsWalk_SprintCostsLess() {
        assertEquals(true, MoveCost.costOf(MoveKind.SPRINT, 0) < MoveCost.costOf(MoveKind.WALK, 0),
                "Спринт обязан стоить дешевле шага: иначе ближняя-дешёвая цель никогда не выиграет у ближней-пешей");
    }

    @Test
    void costOf_NegativeFallHeight_Throws() {
        assertThrows(IllegalArgumentException.class, () -> MoveCost.costOf(MoveKind.FALL, -1),
                "Отрицательная высота падения обязана отвергаться: вверх падать нельзя, знак высоты перепутан");
    }

    @Test
    void fallDamage_NegativeHeight_Throws() {
        assertThrows(IllegalArgumentException.class, () -> MoveCost.fallDamage(-1),
                "Отрицательная высота падения обязана отвергаться: урон считается только для падения вниз");
    }
}
