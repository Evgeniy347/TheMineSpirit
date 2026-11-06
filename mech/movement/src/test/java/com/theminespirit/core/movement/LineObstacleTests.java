package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LineObstacleTests {
    @Test
    void first_WallBetween_NamesBlockCell() {
        Map<BlockCoord, Cell> cells = new HashMap<>();
        cells.put(new BlockCoord(1, 1, 0), Cell.SOLID);
        SafetyView view = coord -> cells.getOrDefault(coord, Cell.PASSABLE);

        Optional<BlockCoord> obstacle = LineObstacle.first(new BlockCoord(0, 1, 0), new BlockCoord(2, 1, 0), view);

        assertEquals(
                Optional.of(new BlockCoord(1, 1, 0)),
                obstacle,
                "Преграда между духом и целью обязана называться клеткой: иначе отказ подхода не говорит, "
                        + "что именно мешает и что расчищать (observe6: стена x=-190 у ступени -189,69,-518)");
    }

    @Test
    void first_NoSolid_Empty() {
        SafetyView view = coord -> Cell.PASSABLE;

        assertTrue(
                LineObstacle.first(new BlockCoord(0, 1, 0), new BlockCoord(3, 1, 2), view)
                        .isEmpty(),
                "Свободная линия не даёт преграды: отказ подхода не должен выдумывать блок");
    }

    @Test
    void first_TargetSolid_NotReported() {
        Map<BlockCoord, Cell> cells = new HashMap<>();
        cells.put(new BlockCoord(1, 1, 0), Cell.SOLID);
        SafetyView view = coord -> cells.getOrDefault(coord, Cell.PASSABLE);

        assertTrue(
                LineObstacle.first(new BlockCoord(0, 1, 0), new BlockCoord(1, 1, 0), view)
                        .isEmpty(),
                "Сама цель не считается преградой: иначе отказ назовёт блок, к которому дух и так не идёт");
    }
}
