package com.theminespirit.core.chunk;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.TownPlan;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

public final class SettlementChunks {
    public static final int WORK_RADIUS_BLOCKS = 16;

    private SettlementChunks() {}

    public static Set<ChunkCoord> forPlan(@Nullable TownPlan plan, Collection<BlockCoord> anchors, int radiusBlocks) {
        Objects.requireNonNull(anchors, "anchors не может быть null: без них поселение не держит даже домов");
        Set<ChunkCoord> chunks = new TreeSet<>(around(anchors, radiusBlocks));
        if (plan != null) {
            chunks.addAll(region(plan));
        }
        return Collections.unmodifiableSet(chunks);
    }

    public static Set<ChunkCoord> region(TownPlan plan) {
        Objects.requireNonNull(plan, "План поселения не может быть null: чанки берутся из его участка");
        ColumnMask area = plan.region();
        int minX = Math.floorDiv(area.minX(), 16);
        int maxX = Math.floorDiv(area.minX() + area.width() - 1, 16);
        int minZ = Math.floorDiv(area.minZ(), 16);
        int maxZ = Math.floorDiv(area.minZ() + area.depth() - 1, 16);
        Set<ChunkCoord> chunks = new TreeSet<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                chunks.add(new ChunkCoord(x, z));
            }
        }
        return Collections.unmodifiableSet(chunks);
    }

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

    public static Set<ChunkCoord> workZone(
            @Nullable TownPlan plan, Collection<BlockCoord> anchors, Collection<BlockCoord> spirits, int spiritRadius) {
        Objects.requireNonNull(spirits, "spirits не может быть null: без них зона работ — только поселение");
        if (spiritRadius < 0) {
            throw new IllegalArgumentException("Радиус чанков духа не может быть отрицательным: " + spiritRadius);
        }
        Set<ChunkCoord> chunks = new TreeSet<>(forPlan(plan, anchors, WORK_RADIUS_BLOCKS));
        for (BlockCoord spirit : new LinkedHashSet<>(spirits)) {
            chunks.addAll(ChunkSquare.chunks(
                    new ChunkCoord(Math.floorDiv(spirit.x(), 16), Math.floorDiv(spirit.z(), 16)), spiritRadius));
        }
        return Collections.unmodifiableSet(chunks);
    }
}
