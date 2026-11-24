package com.theminespirit.core.board;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ConstructionBatches {
    public static final int PER_BATCH = 9;

    public record ChunkCell(int x, int z) {}

    private ConstructionBatches() {}

    public static ChunkCell at(int blockX, int blockZ) {
        return new ChunkCell(blockX >> 4, blockZ >> 4);
    }

    public static List<ChunkCell> spiral(int centerX, int centerZ, int radius) {
        if (radius < 0) {
            throw new IllegalArgumentException("Радиус спирали " + radius + ": расстояние не может быть отрицательным");
        }
        List<ChunkCell> order = new ArrayList<>();
        order.add(new ChunkCell(centerX, centerZ));
        for (int ring = 1; ring <= radius; ring++) {
            ring(order, centerX, centerZ, ring);
        }
        return List.copyOf(order);
    }

    public static List<ChunkCell> batch(List<ChunkCell> order, int index) {
        Objects.requireNonNull(order, "Порядок чанков не может быть null: без него нет пачек");
        if (index < 0) {
            throw new IllegalArgumentException("Пачка " + index + ": пачки считаются с нуля");
        }
        int from = index * PER_BATCH;
        if (from >= order.size()) {
            return List.of();
        }
        int to = Math.min(order.size(), from + PER_BATCH);
        return List.copyOf(order.subList(from, to));
    }

    public static boolean hasMore(List<ChunkCell> order, int index) {
        return (index + 1) * PER_BATCH < order.size();
    }

    private static void ring(List<ChunkCell> order, int centerX, int centerZ, int ring) {
        int minX = centerX - ring;
        int maxX = centerX + ring;
        int minZ = centerZ - ring;
        int maxZ = centerZ + ring;
        for (int x = minX; x <= maxX; x++) {
            order.add(new ChunkCell(x, minZ));
        }
        for (int z = minZ + 1; z <= maxZ; z++) {
            order.add(new ChunkCell(maxX, z));
        }
        for (int x = maxX - 1; x >= minX; x--) {
            order.add(new ChunkCell(x, maxZ));
        }
        for (int z = maxZ - 1; z >= minZ + 1; z--) {
            order.add(new ChunkCell(minX, z));
        }
    }
}
