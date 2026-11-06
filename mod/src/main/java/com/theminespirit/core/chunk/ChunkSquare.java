package com.theminespirit.core.chunk;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

public final class ChunkSquare {
    private ChunkSquare() {}

    public static Set<ChunkCoord> chunks(ChunkCoord center, int radius) {
        if (radius < 0) {
            throw new IllegalArgumentException("Радиус квадрата чанков не может быть отрицательным: " + radius);
        }
        Set<ChunkCoord> chunks = new TreeSet<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                chunks.add(new ChunkCoord(center.x() + dx, center.z() + dz));
            }
        }
        return Collections.unmodifiableSet(chunks);
    }

    public static ChunkDiff diff(ChunkCoord oldCenter, ChunkCoord newCenter, int radius) {
        return ChunkDiff.between(chunks(oldCenter, radius), chunks(newCenter, radius));
    }

    public static Set<ChunkCoord> apply(Set<ChunkCoord> current, ChunkDiff diff) {
        Set<ChunkCoord> result = new LinkedHashSet<>(current);
        result.removeAll(diff.toRemove());
        result.addAll(diff.toAdd());
        return Collections.unmodifiableSet(result);
    }
}
