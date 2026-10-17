package com.theminespirit.neoforge;

import com.theminespirit.core.farming.CropKind;
import com.theminespirit.core.farming.FarmJob;
import com.theminespirit.core.mining.ToolReserve;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

final class FarmActions {
    private static final int NO_SLOT = -1;
    private static final double TOP_FACE_OFFSET = 0.5;

    private final SpiritEntity spirit;
    private final Level level;

    FarmActions(SpiritEntity spirit) {
        this.spirit = Objects.requireNonNull(spirit, "Дух не может быть null: без него некому работать на грядке");
        this.level = spirit.level();
    }

    boolean hasSupplies(FarmJob job, CropKind crop) {
        return switch (job) {
            case TILL -> hoeSlot() != NO_SLOT;
            case PLANT -> count(CropRegistry.plantItem(crop)) > 0;
            case HARVEST -> true;
            case BONE_MEAL -> count(Items.BONE_MEAL) > 0;
        };
    }

    int count(Item item) {
        int total = 0;
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    boolean till(BlockPos ground) {
        int slot = hoeSlot();
        if (slot == NO_SLOT) {
            return false;
        }
        ItemStack hoe = spirit.getInventory().getItem(slot);
        UseOnContext context = new UseOnContext(level, null, InteractionHand.MAIN_HAND, hoe, topFace(ground));
        InteractionResult result = hoe.getItem().useOn(context);
        if (!result.consumesAction() || !level.getBlockState(ground).is(Blocks.FARMLAND)) {
            return false;
        }
        hoe.hurtAndBreak(1, spirit, EquipmentSlot.MAINHAND);
        return true;
    }

    boolean plant(BlockPos ground, CropKind kind) {
        ItemStack seeds = firstStack(CropRegistry.plantItem(kind));
        if (seeds.isEmpty() || !(seeds.getItem() instanceof BlockItem placing)) {
            return false;
        }
        BlockPlaceContext context = new BlockPlaceContext(level, null, InteractionHand.MAIN_HAND, seeds,
                topFace(ground));
        return placing.place(context).consumesAction();
    }

    boolean boneMeal(BlockPos crop) {
        ItemStack meal = firstStack(Items.BONE_MEAL);
        return !meal.isEmpty() && BoneMealItem.applyBonemeal(meal, level, crop, null);
    }

    private int hoeSlot() {
        ToolReserve reserve = MiningTables.toolReserve();
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (stack.isEmpty() || !stack.is(ItemTags.HOES)) {
                continue;
            }
            if (stack.isDamageableItem()
                    && reserve.spent(stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage(),
                            stack.isEnchanted())) {
                continue;
            }
            return slot;
        }
        return NO_SLOT;
    }

    private ItemStack firstStack(Item item) {
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (stack.is(item)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static BlockHitResult topFace(BlockPos ground) {
        return new BlockHitResult(Vec3.atCenterOf(ground).add(0, TOP_FACE_OFFSET, 0), Direction.UP, ground, false);
    }
}
