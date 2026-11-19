package com.theminespirit.neoforge.terminal;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

public interface TerminalCrafter {
    ItemEntity spawnAtLocation(ItemStack stack);

    BlockPos blockPosition();

    Component getDisplayName();
}
