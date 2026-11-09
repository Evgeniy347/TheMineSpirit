package com.theminespirit.neoforge;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.PlotProtection;
import com.theminespirit.neoforge.board.PlotBreakLog;
import com.theminespirit.neoforge.board.SpiritDugStore;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

final class BreakNotice {
    private BreakNotice() {}

    static void broken(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            SpiritEntity spirit,
            String action,
            PlotProtection.Ground ground) {
        SettlementChestWatch.broken(level, pos, state);
        PlotBreakLog.broken(level, pos, spirit, action, ground);
        PlacedBlocksData placed = PlacedBlocksData.get(level);
        placed.unmarkSpirit(pos);
        placed.unmarkTemporary(pos);
        if (PlotGround.natural(state)) {
            SpiritDugStore.get(level).remember(new BlockCoord(pos.getX(), pos.getY(), pos.getZ()));
        }
    }
}
