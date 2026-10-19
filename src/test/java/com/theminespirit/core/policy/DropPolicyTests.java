package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.OptionalInt;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DropPolicyTests {
    private static final BlockCoord FROM = new BlockCoord(7, 70, -3);

    private static SafetyView columnBelow(Cell... downFromStart) {
        return coord -> {
            int depth = FROM.y() - coord.y() - 1;
            if (coord.x() != FROM.x() || coord.z() != FROM.z() || depth < 0 || depth >= downFromStart.length) {
                return Cell.PASSABLE;
            }
            return downFromStart[depth];
        };
    }

    static Stream<Arguments> columns() {
        return Stream.of(
                Arguments.of("пол сразу под клеткой", new Cell[] {Cell.SOLID}, OptionalInt.of(0)),
                Arguments.of("воздух 1, потом пол", new Cell[] {Cell.PASSABLE, Cell.SOLID}, OptionalInt.of(1)),
                Arguments.of(
                        "воздух 2, потом пол: предел",
                        new Cell[] {Cell.PASSABLE, Cell.PASSABLE, Cell.SOLID},
                        OptionalInt.of(2)),
                Arguments.of(
                        "воздух 3: глубже предела",
                        new Cell[] {Cell.PASSABLE, Cell.PASSABLE, Cell.PASSABLE, Cell.SOLID},
                        OptionalInt.empty()),
                Arguments.of("вода сразу под клеткой", new Cell[] {Cell.WATER, Cell.SOLID}, OptionalInt.empty()),
                Arguments.of(
                        "воздух, потом вода", new Cell[] {Cell.PASSABLE, Cell.WATER, Cell.SOLID}, OptionalInt.empty()),
                Arguments.of(
                        "воздух, потом лава", new Cell[] {Cell.PASSABLE, Cell.LAVA, Cell.SOLID}, OptionalInt.empty()),
                Arguments.of("огонь под клеткой", new Cell[] {Cell.FIRE, Cell.SOLID}, OptionalInt.empty()),
                Arguments.of("опасный блок под клеткой", new Cell[] {Cell.HAZARD, Cell.SOLID}, OptionalInt.empty()),
                Arguments.of("песок под клеткой не пол", new Cell[] {Cell.FALLING, Cell.SOLID}, OptionalInt.empty()),
                Arguments.of(
                        "воздух, потом песок",
                        new Cell[] {Cell.PASSABLE, Cell.FALLING, Cell.SOLID},
                        OptionalInt.empty()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("columns")
    void fallBelow_Column_ReturnsDepthOrEmpty(String name, Cell[] column, OptionalInt expected) {
        OptionalInt depth = DropPolicy.fallBelow(FROM, columnBelow(column));

        assertEquals(
                expected,
                depth,
                "Колонна «" + name + "»: глубина падения до пола считается только по воздуху и только до двух блоков");
    }

    @Test
    void maxDrop_Constant_IsTwoBlocks() {
        assertEquals(
                2,
                DropPolicy.MAX_DROP,
                "Предел падения духа 2 блока: падение на 3 уже ранит, а духу нельзя терять здоровье на пути");
    }
}
