package com.theminespirit.neoforge;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

final class BreakNames {
    private BreakNames() {}

    static String blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    static String needToolReason(BlockState state) {
        String kind = RequiredTier.kindOf(state);
        String what = kind.isEmpty() ? "нужен инструмент" : "нужен инструмент (" + kind + ")";
        return what + " для " + blockId(state) + ": подходящий есть, но изношен, запасного в инвентаре нет";
    }
}
