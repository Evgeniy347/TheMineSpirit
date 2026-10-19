package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.BreakSafety;
import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class BreakVetoTests {
    private static final BlockCoord FEET = new BlockCoord(-4, 70, 12);
    private static final BlockCoord UNDER = new BlockCoord(-4, 69, 12);

    private static SafetyView columnUnderTarget(Cell... fromTargetDown) {
        return coord -> {
            int depth = UNDER.y() - coord.y();
            if (coord.x() != UNDER.x() || coord.z() != UNDER.z() || depth < 0 || depth >= fromTargetDown.length) {
                return Cell.PASSABLE;
            }
            return fromTargetDown[depth];
        };
    }

    static Stream<Arguments> columns() {
        Cell s = Cell.SOLID;
        Cell a = Cell.PASSABLE;
        return Stream.of(
                Arguments.of("двойной пол: под целью ещё блок", new Cell[] {s, s}, true),
                Arguments.of("под целью воздух 1, потом пол: падение 2", new Cell[] {s, a, s}, true),
                Arguments.of("под целью воздух 2, потом пол: падение 3", new Cell[] {s, a, a, s}, false),
                Arguments.of("под целью пустота без дна", new Cell[] {s, a, a, a, a}, false),
                Arguments.of("под целью вода", new Cell[] {s, Cell.WATER, s}, false),
                Arguments.of("под целью лава", new Cell[] {s, Cell.LAVA, s}, false),
                Arguments.of("под целью воздух, потом вода", new Cell[] {s, a, Cell.WATER}, false),
                Arguments.of("под целью воздух, потом лава", new Cell[] {s, a, Cell.LAVA}, false),
                Arguments.of("под целью огонь", new Cell[] {s, Cell.FIRE, s}, false),
                Arguments.of("под целью опасный блок", new Cell[] {s, Cell.HAZARD, s}, false),
                Arguments.of("под целью песок", new Cell[] {s, Cell.FALLING, s}, false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("columns")
    void checkBelowFeet_TargetUnderFeet_AllowsOnlyDropsWithinLimit(String name, Cell[] column, boolean allowed) {
        BreakSafety safety = BreakVeto.checkBelowFeet(UNDER, FEET, columnUnderTarget(column));

        assertEquals(
                allowed,
                safety instanceof BreakSafety.Safe,
                "Колонна «" + name
                        + "»: после ломки блока под ногами дух падает не больше чем на 2 блока и только на твёрдый пол. Ответ: "
                        + safety);
    }

    @Test
    void checkBelowFeet_TargetBesideOverPit_IsSafe() {
        BlockCoord beside = new BlockCoord(FEET.x() + 1, FEET.y() - 1, FEET.z());

        BreakSafety safety = BreakVeto.checkBelowFeet(beside, FEET, columnUnderTarget());

        assertEquals(
                new BreakSafety.Safe(),
                safety,
                "Блок сбоку от духа не держит его: вето касается только блока под ногами");
    }

    @Test
    void checkBelowFeet_TargetAtFeetLevel_IsSafe() {
        BreakSafety safety = BreakVeto.checkBelowFeet(FEET, FEET, columnUnderTarget());

        assertEquals(
                new BreakSafety.Safe(),
                safety,
                "Блок на уровне ног духа не держит его: вето касается только блока под ногами");
    }

    @Test
    void checkBelowFeet_TargetTwoBelowFeet_IsSafe() {
        BlockCoord deeper = new BlockCoord(FEET.x(), FEET.y() - 2, FEET.z());

        BreakSafety safety = BreakVeto.checkBelowFeet(deeper, FEET, columnUnderTarget());

        assertEquals(
                new BreakSafety.Safe(), safety, "Блок на два ниже ног: между ним и духом остаётся пол, дух не падает");
    }

    @Test
    void checkBelowFeet_Refusal_NamesTheDepthLimit() {
        BreakSafety safety = BreakVeto.checkBelowFeet(
                UNDER, FEET, columnUnderTarget(Cell.SOLID, Cell.PASSABLE, Cell.PASSABLE, Cell.SOLID));

        BreakSafety.Unsafe unsafe = (BreakSafety.Unsafe) safety;
        assertTrue(
                unsafe.reason().contains("глубже 2 блоков") && unsafe.reason().contains("-4 69 12"),
                "Отказ называет блок и предел падения, чтобы по итогу задачи было видно, что делать. Причина: "
                        + unsafe.reason());
    }
}
