package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PatrolPointsTests {
    private static final BlockCoord CENTER = new BlockCoord(0, 64, 0);

    @Test
    void around_TerrainGiven_OnlyRealStands() {
        Map<String, Integer> heights = Map.of(
                "0,0", 64,
                "4,0", 64,
                "0,4", 64,
                "-4,0", 70,
                "0,-4", 63);
        PatrolPoints.Ground ground = new PatrolPoints.Ground() {
            @Override
            public int feet(int x, int z) {
                Integer found = heights.get(x + "," + z);
                return found == null ? PatrolPoints.NO_GROUND : found;
            }

            @Override
            public boolean free(BlockCoord feet) {
                return feet.x() != 0 || feet.z() != 4;
            }
        };

        List<BlockCoord> points = PatrolPoints.around(CENTER, ground);

        assertEquals(
                List.of(new BlockCoord(0, 64, 0), new BlockCoord(4, 64, 0), new BlockCoord(0, 63, -4)),
                points,
                "Патруль обходит только стоянки: столб +6 и занятая клетка не в счёте, спуск в 1 блок — можно");
    }

    @Test
    void around_NoStands_EmptyInsteadOfImpossiblePatrol() {
        PatrolPoints.Ground solid = new PatrolPoints.Ground() {
            @Override
            public int feet(int x, int z) {
                return 64;
            }

            @Override
            public boolean free(BlockCoord feet) {
                return false;
            }
        };

        assertTrue(
                PatrolPoints.around(CENTER, solid).isEmpty(),
                "Нет ни одной стоянки — патруль не ставится: иначе 5 провалов «0 из 5 точек» (10.11)");
    }

    @Test
    void around_UnloadedColumns_Skipped() {
        PatrolPoints.Ground unloaded = new PatrolPoints.Ground() {
            @Override
            public int feet(int x, int z) {
                return PatrolPoints.NO_GROUND;
            }

            @Override
            public boolean free(BlockCoord feet) {
                throw new AssertionError("Незагруженный столбец не проверяется: неизвестно — не стоянка");
            }
        };

        assertTrue(PatrolPoints.around(CENTER, unloaded).isEmpty(), "Незагруженные чанки не выдумывают точек");
    }
}
