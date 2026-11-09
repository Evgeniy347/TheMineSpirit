package com.theminespirit.neoforge;

import com.theminespirit.core.building.BlockSupport;
import com.theminespirit.core.building.SchematicBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

final class PlaceSupport {
    private PlaceSupport() {}

    static boolean hasSupport(Level level, BlockPos target, BlockState state) {
        if (state.getBlock() instanceof CropBlock || state.getBlock() instanceof NetherWartBlock) {
            return state.canSurvive(level, target);
        }
        if (upperHalfOnLower(level, target, state)) {
            return true;
        }
        if (hanging(state) || onPost(level, target, state)) {
            return state.canSurvive(level, target);
        }
        return BlockSupport.hasSupport(
                        (x, y, z, face) -> {
                            BlockPos pos = new BlockPos(x, y, z);
                            return level.getBlockState(pos).isFaceSturdy(level, pos, direction(face));
                        },
                        target.getX(),
                        target.getY(),
                        target.getZ())
                && state.canSurvive(level, target);
    }

    private static boolean onPost(Level level, BlockPos target, BlockState state) {
        String block = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        String below = BuiltInRegistries.BLOCK
                .getKey(level.getBlockState(target.below()).getBlock())
                .toString();
        return SchematicBlocks.standsOnCenter(block) && SchematicBlocks.isCenterPost(below);
    }

    private static boolean hanging(BlockState state) {
        if (state.hasProperty(BlockStateProperties.HANGING)) {
            return state.getValue(BlockStateProperties.HANGING);
        }
        return state.getBlock() instanceof ChainBlock;
    }

    private static Direction direction(BlockSupport.Face face) {
        return switch (face) {
            case UP -> Direction.UP;
            case DOWN -> Direction.DOWN;
            case NORTH -> Direction.NORTH;
            case SOUTH -> Direction.SOUTH;
            case EAST -> Direction.EAST;
            case WEST -> Direction.WEST;
        };
    }

    private static boolean upperHalfOnLower(Level level, BlockPos target, BlockState state) {
        if (!state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                || state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) != DoubleBlockHalf.UPPER) {
            return false;
        }
        BlockState lower = level.getBlockState(target.below());
        return lower.is(state.getBlock())
                && lower.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && lower.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER;
    }
}
