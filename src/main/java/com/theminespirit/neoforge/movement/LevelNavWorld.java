package com.theminespirit.neoforge.movement;

import com.theminespirit.core.navigation.NavActionCosts;
import com.theminespirit.core.navigation.NavBlockType;
import com.theminespirit.core.navigation.NavChunkSource;
import com.theminespirit.core.navigation.NavWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.MagmaBlock;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class LevelNavWorld implements NavWorld {
    private static final double FLOOR_MIN = 0.5;
    private static final double FLOOR_MAX = 1.0;

    private final Level level;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    public LevelNavWorld(Level level) {
        this.level = level;
    }

    @Override
    public boolean available(int x, int z) {
        return level.hasChunk(x >> 4, z >> 4);
    }

    @Override
    public boolean passable(int x, int y, int z) {
        BlockState state = state(x, y, z);
        return shape(x, y, z).isEmpty()
                && !state.is(BlockTags.LEAVES)
                && !state.getFluidState().is(FluidTags.LAVA)
                && !hazard(state);
    }

    @Override
    public boolean walkable(int x, int y, int z) {
        VoxelShape shape = shape(x, y, z);
        return !state(x, y, z).is(BlockTags.LEAVES)
                && !shape.isEmpty()
                && shape.max(Direction.Axis.Y) >= FLOOR_MIN
                && shape.max(Direction.Axis.Y) <= FLOOR_MAX
                && state(x, y, z).getFluidState().isEmpty();
    }

    @Override
    public boolean water(int x, int y, int z) {
        return fluid(x, y, z).is(FluidTags.WATER);
    }

    @Override
    public boolean climbable(int x, int y, int z) {
        return state(x, y, z).is(BlockTags.CLIMBABLE);
    }

    @Override
    public double breakTicks(int x, int y, int z) {
        BlockState state = state(x, y, z);
        double hardness = state.getDestroySpeed(level, cursor.set(x, y, z));
        if (hardness < 0) {
            return NavActionCosts.COST_INF;
        }
        return hardness == 0 ? 0 : Math.max(1, hardness * 5);
    }

    @Override
    public int minY() {
        return level.getMinBuildHeight();
    }

    @Override
    public int maxY() {
        return level.getMaxBuildHeight();
    }

    public NavChunkSource source() {
        return (x, y, z) -> {
            if (water(x, y, z)) {
                return NavBlockType.WATER;
            }
            if (walkable(x, y, z)) {
                return NavBlockType.SOLID;
            }
            return passable(x, y, z) ? NavBlockType.AIR : NavBlockType.AVOID;
        };
    }

    private BlockState state(int x, int y, int z) {
        return level.getBlockState(cursor.set(x, y, z));
    }

    private FluidState fluid(int x, int y, int z) {
        return level.getFluidState(cursor.set(x, y, z));
    }

    private static boolean hazard(BlockState state) {
        return state.getBlock() instanceof BaseFireBlock
                || state.getBlock() instanceof MagmaBlock
                || state.getBlock() instanceof CactusBlock
                || state.getBlock() instanceof SweetBerryBushBlock
                || state.getBlock() instanceof WebBlock
                || state.getBlock() instanceof PowderSnowBlock;
    }

    private VoxelShape shape(int x, int y, int z) {
        cursor.set(x, y, z);
        return state(x, y, z).getCollisionShape(level, cursor);
    }
}
