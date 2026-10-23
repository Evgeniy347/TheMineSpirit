package com.theminespirit.neoforge;

import com.theminespirit.core.mining.MineOres;
import com.theminespirit.core.mining.ToolChoice;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Кирки духа для правила руды {@link MineOres}: слот, годится ли для дропа, Удача и Шёлковое касание. */
final class PickOptions {
    private PickOptions() {}

    static List<MineOres.Pick> of(SpiritEntity spirit, BlockState state) {
        Level level = spirit.level();
        var enchantments = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        Holder<Enchantment> fortune = enchantments.getHolderOrThrow(Enchantments.FORTUNE);
        Holder<Enchantment> silk = enchantments.getHolderOrThrow(Enchantments.SILK_TOUCH);
        List<MineOres.Pick> picks = new ArrayList<>();
        add(picks, ToolChoice.MAIN_HAND, spirit.getMainHandItem(), state, fortune, silk);
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            add(picks, slot, spirit.getInventory().getItem(slot), state, fortune, silk);
        }
        return picks;
    }

    private static void add(
            List<MineOres.Pick> picks,
            int slot,
            ItemStack stack,
            BlockState state,
            Holder<Enchantment> fortune,
            Holder<Enchantment> silk) {
        if (!(stack.getItem() instanceof PickaxeItem)) {
            return;
        }
        picks.add(new MineOres.Pick(
                slot,
                stack.isCorrectToolForDrops(state),
                stack.getEnchantmentLevel(fortune),
                stack.getEnchantmentLevel(silk) > 0));
    }
}
