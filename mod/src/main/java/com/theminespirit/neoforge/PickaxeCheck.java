package com.theminespirit.neoforge;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

public final class PickaxeCheck {
    private PickaxeCheck() {}

    public static boolean has(SpiritEntity spirit) {
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            if (pickaxe(spirit.getInventory().getItem(slot))) {
                return true;
            }
        }
        return spirit.mainHandOwned() && pickaxe(spirit.getMainHandItem());
    }

    public static boolean nearby(SpiritEntity spirit, double radius) {
        if (!(spirit.level() instanceof ServerLevel server)) {
            return false;
        }
        return server
                .getEntitiesOfClass(SpiritEntity.class, spirit.getBoundingBox().inflate(radius))
                .stream()
                .anyMatch(other -> !other.equals(spirit) && has(other));
    }

    private static boolean pickaxe(ItemStack stack) {
        return !stack.isEmpty()
                && BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("_pickaxe");
    }
}
