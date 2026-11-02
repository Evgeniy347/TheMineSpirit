package com.theminespirit.neoforge.board;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.TerrainGrid;
import com.theminespirit.neoforge.PlotGround;
import java.util.BitSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

public final class GroundProbe {
    private static final int SCAN_DEPTH = 48;

    private GroundProbe() {}

    public static int feet(ServerLevel level, int x, int z, int fallback) {
        BlockPos.MutableBlockPos pos =
                new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        for (int step = 0; step < SCAN_DEPTH; step++) {
            pos.move(0, -1, 0);
            if (!level.isLoaded(pos)) {
                return fallback;
            }
            if (isGround(level.getBlockState(pos))) {
                return pos.getY() + 1;
            }
        }
        return fallback;
    }

    public static int top(ServerLevel level, int x, int z, int fallback) {
        if (!level.isLoaded(new BlockPos(x, level.getMinBuildHeight(), z))) {
            return fallback;
        }
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
    }

    public static TerrainGrid terrain(ServerLevel level, BlockCoord anchor, int radius) {
        int side = 2 * radius + 1;
        int[] feet = new int[side * side];
        BitSet water = new BitSet(side * side);
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int x = anchor.x() + dx;
                int z = anchor.z() + dz;
                int index = TerrainGrid.index(radius, dx, dz);
                if (!level.isLoaded(new BlockPos(x, anchor.y(), z))) {
                    feet[index] = TerrainGrid.NO_GROUND;
                    continue;
                }
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                BlockState surface = level.getBlockState(new BlockPos(x, top, z));
                water.set(index, surface.getFluidState().is(FluidTags.WATER));
                feet[index] = surveyFeet(level, x, z, TerrainGrid.NO_GROUND);
            }
        }
        return TerrainGrid.around(anchor, radius, feet, water);
    }

    private static int surveyFeet(ServerLevel level, int x, int z, int fallback) {
        BlockPos.MutableBlockPos pos =
                new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        for (int step = 0; step < SCAN_DEPTH; step++) {
            pos.move(0, -1, 0);
            if (!level.isLoaded(pos)) {
                return fallback;
            }
            BlockState state = level.getBlockState(pos);
            if ((PlotGround.natural(state) && !state.is(Blocks.COBBLESTONE))
                    || (!state.getFluidState().isEmpty()
                            && !state.getFluidState().is(FluidTags.WATER))) {
                return pos.getY() + 1;
            }
        }
        return fallback;
    }

    private static boolean isGround(BlockState state) {
        return PlotGround.natural(state)
                || (!state.getFluidState().isEmpty() && !state.getFluidState().is(FluidTags.WATER));
    }
}
