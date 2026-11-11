package com.theminespirit.neoforge.board;

import com.theminespirit.core.movement.BlockKind;
import com.theminespirit.core.movement.PitExitPlanner;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class PitWorldView implements PitExitPlanner.World {
    private static final Set<String> SOFT = Set.of(
            "dirt",
            "grass_block",
            "coarse_dirt",
            "rooted_dirt",
            "podzol",
            "mycelium",
            "dirt_path",
            "farmland",
            "clay",
            "mud",
            "muddy_mangrove_roots",
            "moss_block",
            "snow",
            "snow_block",
            "powder_snow",
            "soul_sand",
            "soul_soil",
            "oak_leaves",
            "spruce_leaves",
            "birch_leaves",
            "jungle_leaves",
            "acacia_leaves",
            "dark_oak_leaves",
            "mangrove_leaves",
            "cherry_leaves",
            "azalea_leaves",
            "flowering_azalea_leaves");
    private static final Set<String> FALLING =
            Set.of("sand", "red_sand", "gravel", "suspicious_sand", "suspicious_gravel");

    private final Level level;

    public PitWorldView(Level level) {
        this.level = level;
    }

    @Override
    public BlockKind at(BlockCoord cell) {
        BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
        if (!level.isLoaded(pos)) {
            return BlockKind.HARD;
        }
        return kind(level, pos, level.getBlockState(pos));
    }

    public static BlockKind kind(Level level, BlockPos pos, BlockState state) {
        if (state.getFluidState().is(FluidTags.LAVA)) {
            return BlockKind.LAVA;
        }
        if (state.getFluidState().is(FluidTags.WATER)) {
            return BlockKind.WATER;
        }
        if (state.isAir() || (state.getCollisionShape(level, pos).isEmpty() && state.canBeReplaced())) {
            return BlockKind.AIR;
        }
        if (state.getDestroySpeed(level, pos) < 0) {
            return BlockKind.BEDROCK;
        }
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (FALLING.contains(id)) {
            return BlockKind.FALLING;
        }
        if (SOFT.contains(id)) {
            return BlockKind.SOFT;
        }
        return BlockKind.HARD;
    }
}
