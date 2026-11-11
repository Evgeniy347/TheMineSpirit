package com.theminespirit.neoforge;

import com.theminespirit.core.movement.BlockKind;
import com.theminespirit.core.movement.PitExitPlanner;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.board.PitWorldView;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

final class DigOutPendingView implements PitExitPlanner.World {
    private final Level level;
    private final PitWorldView live;
    private final DigOutRestore restore;

    DigOutPendingView(Level level, DigOutRestore restore) {
        this.level = level;
        this.live = new PitWorldView(level);
        this.restore = restore;
    }

    @Override
    public BlockKind at(BlockCoord cell) {
        @Nullable BlockState before = restore.pendingState(cell);
        if (before == null) {
            return live.at(cell);
        }
        BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
        return PitWorldView.kind(level, pos, before);
    }
}
