package com.theminespirit.neoforge.board;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.DemolitionVerdict;
import com.theminespirit.core.town.FenceDemolition;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.BlockStates;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

final class DemolitionLeftovers {
    private DemolitionLeftovers() {}

    static boolean allowedByProtection(SpiritEntity spirit, BlockPos pos, BlockState state) {
        return DemolitionVerdict.permissibleEither(
                spirit.protectionGate().checkDemolish(pos, state),
                spirit.protectionGate().checkExcavate(pos, state));
    }

    static @Nullable String reason(
            ServerLevel level,
            @Nullable StageSite site,
            PlotPlanBlocks.@Nullable Plan plan,
            BlockCoord at,
            BlockState state) {
        String blockId = BlockStates.idOf(state);
        if (site != null && FenceDemolition.allowed(blockId, site.fenceStrip(), at.x(), at.z())) {
            return "забор плана ограды";
        }
        if (plan != null && plan.planned(at)) {
            return "блок постройки плана поселения";
        }
        if (state.getBlock() instanceof ChestBlock && !ChestDemolition.removable(level, at)) {
            return "сундук в плане, защищён игроком или с неизвестным содержимым — его сперва переносят";
        }
        if (!PlacedBlocksData.get(level).isSpiritMarked(new BlockPos(at.x(), at.y(), at.z()))
                && DemolitionGuard.protectedStray(level, at, blockId)) {
            return "постройка игрока или защищённый блок";
        }
        return null;
    }
}
