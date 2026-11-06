package com.theminespirit.neoforge.crafting;

import com.theminespirit.neoforge.SpiritEntity;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

final class BrewBottles {
    private static final int WATER_SEARCH_HEIGHT = 2;
    private static final int WATER_SEARCH_MIN = 2;

    private BrewBottles() {}

    static int count(SpiritEntity spirit) {
        int water = 0;
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (isWater(stack)) {
                water += stack.getCount();
            }
        }
        return water;
    }

    static ItemStack take(SpiritEntity spirit) {
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (!isWater(stack)) {
                continue;
            }
            spirit.getInventory().setItem(slot, ItemStack.EMPTY);
            return stack.copyWithCount(1);
        }
        return ItemStack.EMPTY;
    }

    static Optional<String> ensure(SpiritEntity spirit, int bottles, int radius) {
        int missing = bottles - count(spirit);
        if (missing <= 0) {
            return Optional.empty();
        }
        int glass = Math.min(missing, spirit.getInventory().countItem(Items.GLASS_BOTTLE));
        if (glass < missing) {
            return Optional.of("не хватает стеклянных бутылок: нужно " + missing + ", есть " + glass);
        }
        if (!waterNearby(spirit, radius)) {
            return Optional.of("рядом нет воды: бутылку воды не из чего набрать");
        }
        spirit.getInventory().removeItemType(Items.GLASS_BOTTLE, missing);
        ItemStack potions = waterPotion();
        potions.setCount(missing);
        ItemStack rest = spirit.getInventory().addItem(potions);
        return rest.isEmpty() ? Optional.empty() : Optional.of("нет места в инвентаре для бутылок воды");
    }

    static ItemStack waterPotion() {
        return PotionContents.createItemStack(Items.POTION, Potions.WATER);
    }

    static boolean isWater(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(Items.POTION)) {
            return false;
        }
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return contents != null && contents.is(Potions.WATER);
    }

    private static boolean waterNearby(SpiritEntity spirit, int radius) {
        Level level = spirit.level();
        int search = Math.max(WATER_SEARCH_MIN, radius);
        BlockPos center = spirit.blockPosition();
        for (int dx = -search; dx <= search; dx++) {
            for (int dz = -search; dz <= search; dz++) {
                for (int dy = -WATER_SEARCH_HEIGHT; dy <= WATER_SEARCH_HEIGHT; dy++) {
                    BlockPos at = center.offset(dx, dy, dz);
                    if (level.isLoaded(at) && level.getBlockState(at).is(Blocks.WATER)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
