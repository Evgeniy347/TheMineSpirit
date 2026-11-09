package com.theminespirit.neoforge;

import com.theminespirit.core.policy.BlockProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

final class BreakProtection {
    private BreakProtection() {}

    static BlockProtection.Decision check(
            SpiritEntity spirit,
            BlockBreaker.Intent intent,
            BlockPos target,
            BlockState targetState,
            boolean escaping,
            boolean rescue) {
        return switch (intent) {
            case HARVEST, CHOP_HARVEST -> spirit.protectionGate().checkHarvest(target, targetState);
            case DEMOLISH -> spirit.protectionGate().checkDemolish(target, targetState);
            case REPAIR -> spirit.protectionGate().checkRepair(target, targetState);
            case EXCAVATE -> spirit.protectionGate().checkExcavate(target, targetState);
            case BREAK, CHOP -> spirit.protectionGate().checkBreakOrEscape(target, targetState, escaping, rescue);
        };
    }
}
