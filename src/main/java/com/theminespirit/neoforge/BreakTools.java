package com.theminespirit.neoforge;

import com.theminespirit.core.mining.ToolChoice;
import com.theminespirit.core.mining.ToolChoice.ToolOption;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

final class BreakTools {
    private BreakTools() {}

    static ToolOption optionFor(int slot, ItemStack stack, BlockState targetState, boolean needsTool) {
        int max = stack.isDamageableItem() ? stack.getMaxDamage() : ToolChoice.UNWEARING;
        int remaining = max > 0 ? max - stack.getDamageValue() : ToolChoice.UNWEARING;
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return new ToolOption(
                slot,
                stack.getDestroySpeed(targetState),
                !needsTool || stack.isCorrectToolForDrops(targetState),
                remaining,
                max,
                stack.isEnchanted(),
                itemId);
    }
}
