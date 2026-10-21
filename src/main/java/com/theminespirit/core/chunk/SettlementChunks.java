package com.theminespirit.core.chunk;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Чанки поселения: дома духов с их участками и лагеря. Набор один на поселение, поэтому число тикетов
 * не зависит от числа духов: дубликаты якорей схлопываются до расчёта.
 */
public final class SettlementChunks {
    public static final int WORK_RADIUS_BLOCKS = 16;

    private SettlementChunks() {}

    public static Set<ChunkCoord> around(Collection<BlockCoord> anchors, int radiusBlocks) {
        Objects.requireNonNull(
                anchors, "anchors не может быть null: поселение без якорей — пустая коллекция, а не null");
        if (radiusBlocks < 0) {
            throw new IllegalArgumentException("Радиус работы поселения не может быть отрицательным: " + radiusBlocks);
        }
        Set<ChunkCoord> chunks = new TreeSet<>();
        for (BlockCoord anchor : new LinkedHashSet<>(anchors)) {
            int minX = Math.floorDiv(anchor.x() - radiusBlocks, 16);
            int maxX = Math.floorDiv(anchor.x() + radiusBlocks, 16);
            int minZ = Math.floorDiv(anchor.z() - radiusBlocks, 16);
            int maxZ = Math.floorDiv(anchor.z() + radiusBlocks, 16);
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    chunks.add(new ChunkCoord(x, z));
                }
            }
        }
        return Collections.unmodifiableSet(chunks);
    }

    public static ChunkDiff transition(Set<ChunkCoord> held, Set<ChunkCoord> settlement, boolean anyPlayerOnline) {
        Objects.requireNonNull(held, "held не может быть null: удержанные чанки задают пустым множеством");
        Objects.requireNonNull(
                settlement, "settlement не может быть null: поселение без чанков задают пустым множеством");
        Set<ChunkCoord> desired = anyPlayerOnline ? settlement : Set.of();
        return ChunkDiff.between(held, desired);
    }
}
