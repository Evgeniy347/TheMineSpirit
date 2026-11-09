package com.theminespirit.neoforge;

import com.theminespirit.core.mining.MineOres;
import com.theminespirit.core.mining.ToolChoice;
import com.theminespirit.core.mining.ToolChoice.ToolOption;
import com.theminespirit.core.mining.ToolReserve;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

final class BreakToolPick {
    private BreakToolPick() {}

    static int luckSlot(SpiritEntity spirit, int forcedSlot, BlockState targetState) {
        if (forcedSlot != BlockBreaker.NO_FORCED_SLOT
                || MineOres.kindOf(BlockStates.idOf(targetState)) != MineOres.Kind.LUCK) {
            return forcedSlot;
        }
        MineOres.Decision decision =
                MineOres.decide(BlockStates.idOf(targetState), PickOptions.of(spirit, targetState));
        if (decision instanceof MineOres.Mine mine && mine.slot().isPresent()) {
            return mine.slot().getAsInt();
        }
        return forcedSlot;
    }

    static List<ToolOption> options(SpiritEntity spirit, int forcedSlot, BlockState targetState) {
        boolean needsTool = targetState.requiresCorrectToolForDrops();
        List<ToolOption> options = new ArrayList<>();
        options.add(new ToolOption(
                ToolChoice.HAND,
                ToolChoice.HAND_SPEED,
                !needsTool,
                ToolChoice.UNWEARING,
                ToolChoice.UNWEARING,
                false,
                ""));
        ItemStack hand = spirit.getMainHandItem();
        if (!hand.isEmpty() && allowed(forcedSlot, ToolChoice.MAIN_HAND)) {
            options.add(BreakTools.optionFor(ToolChoice.MAIN_HAND, hand, targetState, needsTool));
        }
        Container inventory = spirit.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && allowed(forcedSlot, slot)) {
                options.add(BreakTools.optionFor(slot, stack, targetState, needsTool));
            }
        }
        return options;
    }

    static void replaceSpentHandTool(SpiritEntity spirit, BlockState targetState, ToolReserve reserve) {
        ItemStack hand = spirit.getMainHandItem();
        if (hand.isEmpty()
                || !BreakTools.optionFor(ToolChoice.MAIN_HAND, hand, targetState, true)
                        .spent(reserve, MiningTables.repairTiers())) {
            return;
        }
        int source = bestSpareSlot(spirit, targetState, reserve);
        if (source < 0) {
            return;
        }
        ItemStack spare = spirit.getInventory().getItem(source).copy();
        spirit.getInventory().setItem(source, spirit.mainHandOwned() ? hand.copy() : ItemStack.EMPTY);
        spirit.holdInMainHand(spare);
    }

    private static int bestSpareSlot(SpiritEntity spirit, BlockState targetState, ToolReserve reserve) {
        Container inventory = spirit.getInventory();
        List<String> repairTiers = MiningTables.repairTiers();
        int bestSlot = -1;
        double bestSpeed = ToolChoice.HAND_SPEED;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !stack.isCorrectToolForDrops(targetState)) {
                continue;
            }
            ToolOption option = BreakTools.optionFor(slot, stack, targetState, true);
            if (option.spent(reserve, repairTiers)) {
                continue;
            }
            if (option.speed() > bestSpeed || (option.speed() == bestSpeed && bestSlot < 0)) {
                bestSpeed = option.speed();
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    private static boolean allowed(int forcedSlot, int slot) {
        return forcedSlot == BlockBreaker.NO_FORCED_SLOT || forcedSlot == slot;
    }
}
