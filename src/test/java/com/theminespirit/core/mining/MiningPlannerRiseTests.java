package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MiningPlannerRiseTests {
    private static final BlockCoord FROM = new BlockCoord(0, 0, 0);

    @Test
    void nextReachable_HighStandWithRealPath_IsAccepted() {
        CellWorld world = new CellWorld()
                .set(0, 9, 0, Cell.SOLID)
                .set(1, 9, 0, Cell.PASSABLE)
                .set(1, 8, 0, Cell.SOLID);

        MiningPlanner.Outcome outcome =
                MiningPlanner.nextReachable(FROM, List.of(new BlockCoord(0, 9, 0)), world, Set.of(), stand -> true);

        assertEquals(
                new MiningPlanner.Next(new BlockCoord(0, 9, 0), new BlockCoord(1, 9, 0)),
                outcome,
                "Когда путь к высокой стоянке проверен настоящей навигацией, вертикальный порог не должен "
                        + "отбрасывать её: дух поднимается по ступеням и опоре, а не объявляет блок недостижимым "
                        + "(observe13: «стоянка на 119 выше духа — не подняться» при живых ступенях)");
    }

    @Test
    void nextReachable_HighStandWithoutPath_StaysUnreachable() {
        CellWorld world = new CellWorld()
                .set(0, 9, 0, Cell.SOLID)
                .set(1, 9, 0, Cell.PASSABLE)
                .set(1, 8, 0, Cell.SOLID);

        MiningPlanner.Outcome outcome =
                MiningPlanner.nextReachable(FROM, List.of(new BlockCoord(0, 9, 0)), world, Set.of(), stand -> false);

        assertEquals(
                new MiningPlanner.Exhausted(0, 0, 1),
                outcome,
                "Высокая стоянка без проверенного пути остаётся недостижимой: план не ведёт духа к блоку, "
                        + "куда навигация не находит дороги");
    }
}
