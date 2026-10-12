package com.theminespirit.neoforge;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.perception.Terrain;
import com.theminespirit.core.perception.TerrainView;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;

public final class LoadedTerrain implements TerrainView {
    private final Level level;
    private int reads;

    public LoadedTerrain(Level level) {
        this.level = Objects.requireNonNull(level, "Уровень не может быть null: по нему читается местность");
    }

    @Override
    public Terrain at(BlockCoord coord) {
        Objects.requireNonNull(coord, "Координата не может быть null: неизвестно, какую клетку читать");
        if (level.isOutsideBuildHeight(coord.y())) {
            return Terrain.AIR;
        }
        LevelChunk chunk = level.getChunkSource().getChunkNow(coord.x() >> 4, coord.z() >> 4);
        if (chunk == null) {
            return Terrain.UNLOADED;
        }
        reads++;
        BlockPos pos = new BlockPos(coord.x(), coord.y(), coord.z());
        return toTerrain(chunk.getBlockState(pos), pos);
    }

    public int reads() {
        return reads;
    }

    private Terrain toTerrain(BlockState state, BlockPos pos) {
        FluidState fluid = state.getFluidState();
        if (fluid.is(FluidTags.LAVA)) {
            return Terrain.LAVA;
        }
        if (fluid.is(FluidTags.WATER)) {
            return Terrain.WATER;
        }
        if (state.isAir() || state.getCollisionShape(level, pos).isEmpty()) {
            return Terrain.AIR;
        }
        return Terrain.SOLID;
    }
}
