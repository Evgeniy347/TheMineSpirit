package com.theminespirit.neoforge;

import com.theminespirit.core.movement.SealedSpace;
import com.theminespirit.core.observation.BlockCoord;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

record SealedView(Level level) implements SealedSpace.View {
    @Override
    public boolean passable(BlockCoord cell) {
        BlockPos pos = pos(cell);
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof FenceGateBlock
                || state.getBlock() instanceof TrapDoorBlock) {
            return true;
        }
        return DigOutBackfill.passable(level, pos);
    }

    @Override
    public boolean swimmable(BlockCoord cell) {
        BlockPos pos = pos(cell);
        return level.isLoaded(pos) && level.getFluidState(pos).is(FluidTags.WATER);
    }

    @Override
    public boolean sky(BlockCoord cell) {
        return DigOutScript.open(level, pos(cell));
    }

    static BlockPos pos(BlockCoord cell) {
        return new BlockPos(cell.x(), cell.y(), cell.z());
    }
}
