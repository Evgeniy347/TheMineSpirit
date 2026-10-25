package com.theminespirit.neoforge.policy;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

final class SpiritNodeEvaluator extends WalkNodeEvaluator {
    @Override
    public PathType getPathType(PathfindingContext context, int x, int y, int z) {
        BlockState state = context.getBlockState(new BlockPos(x, y, z));
        if (state.getBlock() instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN)) {
            return PathType.WALKABLE_DOOR;
        }
        if (state.getBlock() instanceof DoorBlock door
                && door.type().canOpenByHand()
                && !state.getValue(DoorBlock.OPEN)) {
            return PathType.WALKABLE_DOOR;
        }
        if (state.is(Blocks.COBWEB)) {
            return PathType.DAMAGE_OTHER;
        }
        return super.getPathType(context, x, y, z);
    }
}
