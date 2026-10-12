package com.theminespirit.neoforge.policy;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

final class SpiritNodeEvaluator extends WalkNodeEvaluator {
    @Override
    public PathType getPathType(PathfindingContext context, int x, int y, int z) {
        if (context.getBlockState(new BlockPos(x, y, z)).is(Blocks.COBWEB)) {
            return PathType.DAMAGE_OTHER;
        }
        return super.getPathType(context, x, y, z);
    }
}
