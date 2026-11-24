package com.theminespirit.neoforge.board;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.DemolitionVerdict;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.BlockStates;
import com.theminespirit.neoforge.SpiritEntity;
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
        if (plan != null && plan.planned(at)) {
            return "блок постройки плана поселения";
        }
        if (state.getBlock() instanceof ChestBlock && !ChestDemolition.removable(level, at)) {
            return "сундук в плане склада/двора или с неизвестным содержимым — его переносит задание сноса";
        }
        if (DemolitionGuard.protectedStray(level, at, blockId)) {
            return "защищённый блок";
        }
        return null;
    }
}
