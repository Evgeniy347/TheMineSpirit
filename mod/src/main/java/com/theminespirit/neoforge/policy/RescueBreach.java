package com.theminespirit.neoforge.policy;

import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.policy.PolicyId;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class RescueBreach {
    private RescueBreach() {}

    public static boolean breakable(BlockState state) {
        return !state.hasBlockEntity()
                && !(state.getBlock() instanceof DoorBlock)
                && !(state.getBlock() instanceof FenceGateBlock)
                && !(state.getBlock() instanceof TrapDoorBlock)
                && !(state.getBlock() instanceof BedBlock);
    }

    static BlockProtection.Decision allow(SpiritEntity spirit, BlockPos pos, BlockState state, boolean rescue) {
        if (!rescue || !(spirit.level() instanceof ServerLevel server)) {
            return new BlockProtection.Refused(PolicyId.PROTECTED_ZONE, "не спасение");
        }
        StageSite site = SettlementPlotStore.get(server).site().orElse(null);
        if (site == null || !site.protectionCell(pos.getX(), pos.getY(), pos.getZ())) {
            return new BlockProtection.Refused(PolicyId.PROTECTED_ZONE, "не клетка плана");
        }
        if (!breakable(state)) {
            return new BlockProtection.Refused(PolicyId.PROTECTED_ZONE, "сундук, дверь или люк не ломаются");
        }
        PlanBreachData.get(server)
                .record(
                        pos,
                        ProtectionGate.blockId(state),
                        spirit.getUUID(),
                        spirit.level().getGameTime());
        return new BlockProtection.Allowed();
    }
}
