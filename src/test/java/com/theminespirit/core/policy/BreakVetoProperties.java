package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.BreakSafety;
import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Arrays;
import java.util.Random;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class BreakVetoProperties {
    private static final BlockCoord FEET = new BlockCoord(3, 40, -9);
    private static final BlockCoord UNDER = new BlockCoord(3, 39, -9);
    private static final Cell[] PALETTE = {Cell.SOLID, Cell.SOLID, Cell.PASSABLE, Cell.PASSABLE, Cell.WATER, Cell.LAVA, Cell.FIRE, Cell.HAZARD, Cell.FALLING};

    @Property(tries = 200)
    void checkBelowFeet_RandomColumn_AllowedExactlyWhenSpiritLandsOnSolidWithinTwoBlocks(@ForAll @IntRange(min = 0, max = 1_000_000) int seed) {
        Random random = new Random(seed);
        Cell[] column = new Cell[8];
        for (int i = 0; i < column.length; i++) {
            column[i] = PALETTE[random.nextInt(PALETTE.length)];
        }
        int[] reads = {0};

        BreakSafety safety = BreakVeto.checkBelowFeet(UNDER, FEET, coord -> {
            reads[0]++;
            int index = UNDER.y() - coord.y();
            return index >= 0 && index < column.length ? column[index] : Cell.PASSABLE;
        });

        assertEquals(landsSafely(column), safety instanceof BreakSafety.Safe,
                "Ломка разрешена ровно тогда, когда после неё дух падает не больше чем на 2 блока на твёрдый пол. Колонна под целью: "
                        + Arrays.toString(column) + ", ответ: " + safety);
        assertTrue(reads[0] <= 3, "Проверка читает не больше трёх клеток под целью (предел падения плюс клетка пола): иначе ломка тормозит тик. Чтений: " + reads[0]);
    }

    private static boolean landsSafely(Cell[] column) {
        int fall = 1;
        for (int index = 1; index < column.length + 4; index++) {
            Cell below = index < column.length ? column[index] : Cell.PASSABLE;
            if (below == Cell.SOLID) {
                return fall <= 2;
            }
            if (below != Cell.PASSABLE) {
                return false;
            }
            fall++;
        }
        return false;
    }
}
