package com.theminespirit.neoforge;

import com.theminespirit.core.chunk.ChunkCoord;
import com.theminespirit.core.chunk.ChunkDiff;
import com.theminespirit.core.chunk.ChunkSquare;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;

final class SpiritChunkLoader {
    private final SpiritEntity spirit;
    private ChunkCoord trackedCenter;
    private int trackedRadius = -1;
    private Set<ChunkCoord> trackedChunks;

    SpiritChunkLoader(SpiritEntity spirit) {
        this.spirit = spirit;
    }

    Set<ChunkCoord> trackedChunks() {
        return trackedChunks == null ? Set.of() : trackedChunks;
    }

    void tick() {
        if (!(spirit.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        SpiritRegistry.get(serverLevel)
                .markAlive(
                        spirit.getUUID(), new BlockCoord(spirit.getBlockX(), spirit.getBlockY(), spirit.getBlockZ()));
        ChunkCoord center = new ChunkCoord(spirit.chunkPosition().x, spirit.chunkPosition().z);
        int radius = SpiritConfig.radiusFor(serverLevel);
        if (trackedChunks != null && center.equals(trackedCenter) && radius == trackedRadius) {
            return;
        }
        Set<ChunkCoord> desired = ChunkSquare.chunks(center, radius);
        ChunkDiff diff = trackedChunks == null ? ChunkDiff.initial(desired) : ChunkDiff.between(trackedChunks, desired);
        if (!diff.isEmpty()) {
            SpiritChunkTickets.apply(serverLevel, spirit, diff);
        }
        trackedCenter = center;
        trackedRadius = radius;
        trackedChunks = desired;
    }

    void release() {
        if (!(spirit.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        SpiritRegistry.get(serverLevel).markDead(spirit.getUUID());
        if (trackedChunks == null) {
            return;
        }
        SpiritChunkTickets.apply(serverLevel, spirit, new ChunkDiff(Set.of(), trackedChunks));
        trackedChunks = null;
        trackedCenter = null;
        trackedRadius = -1;
    }
}
