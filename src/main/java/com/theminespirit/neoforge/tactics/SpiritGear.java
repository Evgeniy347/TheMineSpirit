package com.theminespirit.neoforge.tactics;

import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class SpiritGear {
    public static final double HAND_DAMAGE = 1.0;
    public static final int ATTACK_COOLDOWN_TICKS = 20;

    private final SpiritEntity spirit;

    public SpiritGear(SpiritEntity spirit) {
        this.spirit = spirit;
    }

    public double meleeDamagePerSecond() {
        return bestMeleeDamage() * 20.0 / ATTACK_COOLDOWN_TICKS;
    }

    public double bestMeleeDamage() {
        double best = HAND_DAMAGE;
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            best = Math.max(best, attackDamage(spirit.getInventory().getItem(slot)));
        }
        return best;
    }

    public int blocks() {
        int total = 0;
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (placeable(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public boolean hasBow() {
        return count(Items.BOW) > 0;
    }

    public int arrows() {
        return hasBow() ? arrowCount() : 0;
    }

    public boolean hasWaterBucket() {
        return count(Items.WATER_BUCKET) > 0;
    }

    public int healItems() {
        return count(Items.GOLDEN_APPLE) + count(Items.ENCHANTED_GOLDEN_APPLE);
    }

    public boolean pourWater(BlockPos pos) {
        if (!spirit.level().getBlockState(pos).canBeReplaced()) {
            return false;
        }
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (!stack.is(Items.WATER_BUCKET)) {
                continue;
            }
            if (!spirit.level().setBlock(pos, Blocks.WATER.defaultBlockState(), 3)) {
                return false;
            }
            spirit.getInventory().setItem(slot, new ItemStack(Items.BUCKET));
            return true;
        }
        return false;
    }

    public ItemStack takeArrow() {
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (stack.getItem() instanceof ArrowItem) {
                ItemStack one = stack.copyWithCount(1);
                stack.shrink(1);
                return one;
            }
        }
        return ItemStack.EMPTY;
    }

    public ItemStack bow() {
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (stack.is(Items.BOW)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public void holdMelee() {
        ItemStack best = ItemStack.EMPTY;
        double bestDamage = HAND_DAMAGE;
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            double damage = attackDamage(stack);
            if (damage > bestDamage) {
                best = stack;
                bestDamage = damage;
            }
        }
        hold(best);
    }

    public void holdBow() {
        hold(bow());
    }

    public void holdNothing() {
        hold(ItemStack.EMPTY);
    }

    private void hold(ItemStack stack) {
        ItemStack held = spirit.getItemBySlot(EquipmentSlot.MAINHAND);
        if (stack.isEmpty()) {
            if (!held.isEmpty()) {
                spirit.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            }
            return;
        }
        if (!ItemStack.isSameItemSameComponents(held, stack)) {
            spirit.setItemSlot(EquipmentSlot.MAINHAND, stack.copyWithCount(1));
            spirit.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }
    }

    private int arrowCount() {
        int total = 0;
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (stack.getItem() instanceof ArrowItem) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private int count(Item item) {
        return spirit.getInventory().countItem(item);
    }

    private static double attackDamage(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0;
        }
        ItemAttributeModifiers modifiers =
                stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        double[] total = {HAND_DAMAGE};
        modifiers.forEach(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.value().equals(Attributes.ATTACK_DAMAGE.value())
                    && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                total[0] += modifier.amount();
            }
        });
        return total[0] > HAND_DAMAGE ? total[0] : 0.0;
    }

    private static boolean placeable(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        BlockState state = blockItem.getBlock().defaultBlockState();
        return state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                && !(blockItem.getBlock() instanceof EntityBlock)
                && !(blockItem.getBlock() instanceof FallingBlock)
                && !(blockItem.getBlock() instanceof TntBlock);
    }
}
