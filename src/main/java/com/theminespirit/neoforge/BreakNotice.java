package com.theminespirit.neoforge;

import com.theminespirit.neoforge.board.PlotEditLog;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

final class BreakNotice {
    private BreakNotice() {}

    static void broken(ServerLevel level, BlockPos pos, BlockState state, SpiritEntity spirit) {
        SettlementChestWatch.broken(level, pos, state);
        PlotEditLog.broken(level, pos, spirit);
    }
}
