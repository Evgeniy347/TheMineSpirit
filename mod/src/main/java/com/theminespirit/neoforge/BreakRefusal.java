package com.theminespirit.neoforge;

import com.theminespirit.core.policy.PolicyId;
import com.theminespirit.core.town.PlotProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

final class BreakRefusal {
    private final SpiritEntity spirit;
    private final BlockBreaker.Intent intent;

    BreakRefusal(SpiritEntity spirit, BlockBreaker.Intent intent) {
        this.spirit = spirit;
        this.intent = intent;
    }

    static PlotProtection.Ground groundFor(BlockBreaker.Intent intent) {
        return switch (intent) {
            case DEMOLISH -> PlotProtection.Ground.PLAN_DEMOLISH;
            case REPAIR -> PlotProtection.Ground.REPAIR;
            case EXCAVATE -> PlotProtection.Ground.PLAN_EXCAVATE;
            case BREAK, HARVEST, CHOP, CHOP_HARVEST -> PlotProtection.Ground.NONE;
        };
    }

    BlockBreaker.Begin refused(BlockPos target, BlockBreaker.RefuseReason reason, String message) {
        if (spirit.level() instanceof ServerLevel server) {
            SpiritBlocks.refuse(server, spirit, target, intent.name(), "ломка", message);
        }
        return new BlockBreaker.Refused(reason, message);
    }

    BlockBreaker.Begin protectedBlock(BlockPos target, PolicyId policy, String reason) {
        if (spirit.level() instanceof ServerLevel server) {
            SpiritBlocks.refuse(server, spirit, target, intent.name(), "ломка", reason);
        }
        return new BlockBreaker.Protected(policy, reason);
    }
}
