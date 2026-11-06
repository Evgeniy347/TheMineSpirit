package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MiningPlannerDigInTests {
    private static final BlockCoord FROM = new BlockCoord(0, 0, 0);

    @Test
    void nextReachable_OnlyUnreachableStand_StillReturnsForDigIn() {
        CellWorld world = new CellWorld().set(0, 0, 0, Cell.SOLID).set(1, -1, 0, Cell.SOLID);

        MiningPlanner.Outcome outcome =
                MiningPlanner.nextReachable(FROM, List.of(new BlockCoord(0, 0, 0)), world, Set.of(), stand -> false);

        assertEquals(
                new MiningPlanner.Next(new BlockCoord(0, 0, 0), new BlockCoord(1, 0, 0)),
                outcome,
                "Если путь к стоянке не подтвердился, цель всё равно отдаётся подходу: дух врезается в породу "
                        + "и расчищает соседнюю клетку, иначе глубокий конец шахты навсегда «без стоянки»");
    }
}
