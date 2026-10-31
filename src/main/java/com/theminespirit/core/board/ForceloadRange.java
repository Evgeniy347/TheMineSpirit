package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongPredicate;

public final class ForceloadRange {
    public static final int DEFAULT_SEARCH_CHUNKS = 48;

    private ForceloadRange() {}

    public static boolean inside(BlockCoord point, LongPredicate forced) {
        Objects.requireNonNull(point, "Точка не может быть null: по ней видно, в каком чанке стоит дух");
        Objects.requireNonNull(forced, "Проверка чанка не может быть null: иначе не отличить помеченный чанк");
        return forced.test(chunkKey(point.x() >> 4, point.z() >> 4));
    }

    public static Optional<BlockCoord> returnPoint(
            BlockCoord position, LongPredicate forced, BlockCoord home, int maxChunks) {
        Objects.requireNonNull(position, "Позиция духа не может быть null: от неё ищут ближайший чанк");
        Objects.requireNonNull(home, "Дом не может быть null: пустой дом задают координатой духа");
        if (maxChunks < 1) {
            throw new IllegalArgumentException("maxChunks = " + maxChunks + ": поиск чанка идёт хотя бы на один чанк");
        }
        if (inside(position, forced)) {
            return Optional.empty();
        }
        if (inside(home, forced)) {
            return Optional.of(home);
        }
        return nearestChunk(home, forced, maxChunks).map(chunk -> chunkCenter(chunk, home.y()));
    }

    private static Optional<long[]> nearestChunk(BlockCoord around, LongPredicate forced, int maxChunks) {
        int centerX = around.x() >> 4;
        int centerZ = around.z() >> 4;
        long[] best = null;
        long bestDistance = Long.MAX_VALUE;
        for (int radius = 0; radius <= maxChunks; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    int chunkX = centerX + dx;
                    int chunkZ = centerZ + dz;
                    if (!forced.test(chunkKey(chunkX, chunkZ))) {
                        continue;
                    }
                    long x = (long) chunkX * 16 + 8 - around.x();
                    long z = (long) chunkZ * 16 + 8 - around.z();
                    long distance = x * x + z * z;
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = new long[] {chunkX, chunkZ};
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static BlockCoord chunkCenter(long[] chunk, int y) {
        return new BlockCoord((int) chunk[0] * 16 + 8, y, (int) chunk[1] * 16 + 8);
    }

    public static long chunkKey(int chunkX, int chunkZ) {
        return (chunkX & 0xFFFFFFFFL) | ((chunkZ & 0xFFFFFFFFL) << 32);
    }
}
