package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class MineCellStandsDigInTests {
    @Test
    void of_TargetEnclosedInRock_NearReachableStandOfferedForDigIn() {
        BlockCoord target = new BlockCoord(2, 0, 0);
        BlockCoord feet = new BlockCoord(0, 0, 0);
        CellWorld world = new CellWorld()
                .set(0, -1, 0, Cell.SOLID)
                .set(1, -1, 0, Cell.SOLID)
                .set(2, -1, 0, Cell.SOLID)
                .set(1, 0, 0, Cell.SOLID)
                .set(3, 0, 0, Cell.SOLID)
                .set(2, 0, 1, Cell.SOLID)
                .set(2, 0, -1, Cell.SOLID)
                .set(2, 0, 0, Cell.SOLID)
                .set(2, 1, 0, Cell.SOLID);

        List<BlockCoord> stands = MineCellStands.of(target, feet, world, stand -> true, true);

        assertTrue(
                !stands.isEmpty(),
                "Цель в закрытой породе — стоянка берётся из уже доступной выработки рядом, и дух врезается: "
                        + stands);
    }
}
