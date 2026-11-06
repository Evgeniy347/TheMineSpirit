package com.theminespirit.neoforge;

import com.theminespirit.core.mining.SoilLayer;
import com.theminespirit.core.observation.BlockCoord;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

final class SoilCells {
    private final Level level;
    private final Map<Long, OptionalInt> water = new HashMap<>();

    SoilCells(Level level) {
        this.level = level;
    }

    boolean takeable(BlockCoord coord) {
        return SoilLayer.takeable(cell(coord));
    }

    boolean takeableGranular(BlockCoord coord) {
        return SoilLayer.takeableGranular(cell(coord));
    }

    private SoilLayer.Cell cell(BlockCoord coord) {
        BlockPos pos = new BlockPos(coord.x(), coord.y(), coord.z());
        return new SoilLayer.Cell(
                coord.y(),
                ground(level.getBlockState(pos)),
                ground(level.getBlockState(pos.above())),
                ground(level.getBlockState(pos.below())),
                waterNear(coord.x(), coord.z()));
    }

    private SoilLayer.Ground ground(BlockState state) {
        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)) {
            return SoilLayer.Ground.TURF;
        }
        if (state.getFluidState().is(FluidTags.WATER)) {
            return SoilLayer.Ground.WATER;
        }
        if (state.is(BlockTags.DIRT)) {
            return SoilLayer.Ground.SOIL;
        }
        if (state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.COBBLESTONE)) {
            return SoilLayer.Ground.STONE;
        }
        if (state.isAir()
                || state.canBeReplaced()
                || state.getCollisionShape(level, BlockPos.ZERO).isEmpty()) {
            return SoilLayer.Ground.OPEN;
        }
        return SoilLayer.Ground.OTHER;
    }

    private OptionalInt waterNear(int x, int z) {
        return water.computeIfAbsent(BlockPos.asLong(x, 0, z), key -> scanWater(x, z));
    }

    private OptionalInt scanWater(int x, int z) {
        int radius = SoilLayer.WATER_RADIUS;
        OptionalInt top = OptionalInt.empty();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x + dx, z + dz) - 1;
                pos.set(x + dx, surface, z + dz);
                if (level.isLoaded(pos) && level.getFluidState(pos).is(FluidTags.WATER)) {
                    top = OptionalInt.of(Math.max(top.orElse(Integer.MIN_VALUE), surface));
                }
            }
        }
        return top;
    }
}
