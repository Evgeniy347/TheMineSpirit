package com.theminespirit.neoforge;

import com.theminespirit.core.observation.BlockBox;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.SectionCursor;
import java.util.Optional;
import java.util.Set;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

final class LoadedWorld {
    private LoadedWorld() {
    }

    static Optional<BlockBox> cube(Level level, BlockCoord center, int radius) {
        return BlockBox.cube(center, radius).clampY(level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
    }

    static SectionCursor cursor(Optional<BlockBox> box, BlockCoord center) {
        return box.map(cube -> SectionCursor.of(cube, center)).orElseGet(SectionCursor::empty);
    }

    static LevelChunk chunk(Level level, int chunkX, int chunkZ, Set<Long> skippedChunks) {
        if (level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) instanceof LevelChunk chunk) {
            return chunk;
        }
        skippedChunks.add(ChunkPos.asLong(chunkX, chunkZ));
        return null;
    }
}
