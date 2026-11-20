package com.theminespirit.core.movement;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;

final class PitGround {
    private PitGround() {}

    static int surface(BlockCoord feet, PitExitPlanner.World world) {
        List<Integer> heights = new ArrayList<>();
        for (int dx = -PitExitPlanner.SCAN_RADIUS; dx <= PitExitPlanner.SCAN_RADIUS; dx++) {
            for (int dz = -PitExitPlanner.SCAN_RADIUS; dz <= PitExitPlanner.SCAN_RADIUS; dz++) {
                heights.add(
                        groundY(feet.x() + dx, feet.z() + dz, feet.y(), world).orElse(feet.y()));
            }
        }
        Collections.sort(heights);
        int index = Math.min(heights.size() - 1, (int) Math.round((heights.size() - 1) * 0.9));
        return heights.get(index);
    }

    static int groundYOrFeet(int x, int z, int from, PitExitPlanner.World world) {
        return groundY(x, z, from, world).orElse(from);
    }

    static OptionalInt groundY(int x, int z, int from, PitExitPlanner.World world) {
        for (int y = from; y <= from + PitExitPlanner.MAX_RISE; y++) {
            BlockCoord cell = new BlockCoord(x, y, z);
            if (PitExitPlanner.passable(world.at(cell))
                    && PitExitPlanner.passable(world.at(above(cell)))
                    && PitExitPlanner.supported(world.at(below(cell)))) {
                return OptionalInt.of(y);
            }
        }
        return OptionalInt.empty();
    }

    static boolean within(BlockCoord feet, BlockCoord cell) {
        return Math.abs(cell.x() - feet.x()) <= PitExitPlanner.RADIUS
                && Math.abs(cell.z() - feet.z()) <= PitExitPlanner.RADIUS;
    }

    static BlockCoord above(BlockCoord cell) {
        return new BlockCoord(cell.x(), cell.y() + 1, cell.z());
    }

    static BlockCoord below(BlockCoord cell) {
        return new BlockCoord(cell.x(), cell.y() - 1, cell.z());
    }

    static int[][] sides() {
        return new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    }
}
