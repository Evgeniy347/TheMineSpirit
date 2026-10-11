package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

final class CountingOreView implements OreView {
    private final Set<BlockCoord> ores = new HashSet<>();
    private int reads;

    CountingOreView withOre(int x, int y, int z) {
        ores.add(new BlockCoord(x, y, z));
        return this;
    }

    CountingOreView withOre(BlockCoord coord) {
        ores.add(coord);
        return this;
    }

    Set<BlockCoord> ores() {
        return ores;
    }

    int reads() {
        return reads;
    }

    @Override
    public boolean isOre(BlockCoord coord) {
        reads++;
        return ores.contains(coord);
    }

    Set<BlockCoord> component(BlockCoord start) {
        Set<BlockCoord> component = new HashSet<>();
        if (!ores.contains(start)) {
            return component;
        }
        Deque<BlockCoord> queue = new ArrayDeque<>();
        queue.add(start);
        component.add(start);
        while (!queue.isEmpty()) {
            BlockCoord current = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        BlockCoord neighbor = new BlockCoord(current.x() + dx, current.y() + dy, current.z() + dz);
                        if (ores.contains(neighbor) && component.add(neighbor)) {
                            queue.add(neighbor);
                        }
                    }
                }
            }
        }
        return component;
    }
}
