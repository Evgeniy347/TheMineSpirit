package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReachStandsLiveTests {
    private static final BlockCoord CHEST = new BlockCoord(-171, 71, -487);
    private static final BlockCoord FEET = new BlockCoord(-176, 68, -510);
    private static final BlockCoord BEHIND_WALL = new BlockCoord(-171, 70, -488);
    private static final BlockCoord AROUND = new BlockCoord(-169, 70, -488);

    @Test
    void liveWarehouseStand_BehindBarrier_SkippedForReachableStand() {
        Set<BlockCoord> standable = new HashSet<>();
        standable.add(BEHIND_WALL);
        standable.add(AROUND);
        ReachPlanner.World world = new ReachPlanner.World() {
            @Override
            public boolean ready(BlockCoord cell) {
                return true;
            }

            @Override
            public boolean standable(BlockCoord feet) {
                return standable.contains(feet);
            }
        };

        Optional<BlockCoord> stand = ReachStands.next(
                new SpiritReach(3, 6, 3),
                FEET,
                List.of(CHEST),
                world,
                Set.of(),
                candidate -> !candidate.equals(BEHIND_WALL),
                8);

        assertTrue(stand.isPresent(), "у живого склада есть достижимая стоянка: " + stand);
        assertNotEquals(
                BEHIND_WALL,
                stand.orElseThrow(),
                "стоянка -171,70,-488 за преградой не выбирается: дух не идёт в тупик "
                        + "(observe10 A6: «путь упёрся в тупик, стоянка за преградой»), фактически " + stand);
        assertTrue(
                stand.orElseThrow().equals(AROUND), "выбирается ближайшая достижимая стоянка вокруг сундука: " + stand);
    }

    @Test
    void livePit_AllStandsBehindBarrier_NoStandInsteadOfFirst() {
        Set<BlockCoord> standable = new HashSet<>();
        standable.add(BEHIND_WALL);
        standable.add(AROUND);
        ReachPlanner.World world = new ReachPlanner.World() {
            @Override
            public boolean ready(BlockCoord cell) {
                return true;
            }

            @Override
            public boolean standable(BlockCoord feet) {
                return standable.contains(feet);
            }
        };

        Optional<BlockCoord> stand = ReachStands.next(
                new SpiritReach(3, 15, 3), FEET, List.of(CHEST), world, Set.of(), candidate -> false, 8);

        assertTrue(
                stand.isEmpty(),
                "Ни одна стоянка не достижима пешком — честный отказ, а не первая стоянка за преградой: " + stand);
    }
}
