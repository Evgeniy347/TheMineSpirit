package com.theminespirit.neoforge;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.board.PlotEditLog;
import com.theminespirit.neoforge.board.SpiritDugStore;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

final class BreakNotice {
    private BreakNotice() {}

    static void broken(ServerLevel level, BlockPos pos, BlockState state, SpiritEntity spirit) {
        SettlementChestWatch.broken(level, pos, state);
        PlotEditLog.broken(level, pos, spirit);
        if (PlotGround.natural(state)) {
            SpiritDugStore.get(level).remember(new BlockCoord(pos.getX(), pos.getY(), pos.getZ()));
        }
    }
}
