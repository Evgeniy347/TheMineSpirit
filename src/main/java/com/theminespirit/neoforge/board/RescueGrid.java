package com.theminespirit.neoforge.board;

import com.theminespirit.core.movement.StuckSituation;
import com.theminespirit.core.observation.BlockCoord;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

final class RescueGrid implements StuckSituation.Grid {
    private final Level level;
    private final BlockPos feet;

    RescueGrid(Level level, BlockPos feet) {
        this.level = level;
        this.feet = feet;
    }

    @Override
    public boolean passable(BlockCoord cell) {
        BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
        if (!level.isLoaded(pos)) {
            return true;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof FenceGateBlock
                || state.getBlock() instanceof TrapDoorBlock) {
            return true;
        }
        return state.getCollisionShape(level, pos).isEmpty()
                && state.getFluidState().isEmpty();
    }

    @Override
    public int floorY(int x, int z) {
        int y = feet.getY() - 1;
        if (passable(new BlockCoord(x, y, z))) {
            return feet.getY();
        }
        int top = feet.getY() + StuckSituation.PASSAGE_HEIGHT;
        while (y <= top && !passable(new BlockCoord(x, y, z))) {
            y++;
        }
        return y;
    }
}
