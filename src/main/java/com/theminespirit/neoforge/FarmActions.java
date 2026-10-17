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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
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

    boolean plantOnSide(BlockPos support, Direction side, CropKind kind) {
        ItemStack seeds = firstStack(CropRegistry.plantItem(kind));
        if (seeds.isEmpty() || !(seeds.getItem() instanceof BlockItem placing)) {
            return false;
        }
        Direction facing = side.getOpposite();
        BlockPlaceContext context = new DirectionalPlaceContext(level, support.relative(side), facing, seeds, facing);
        return placing.place(context).consumesAction();
    }

    boolean boneMeal(BlockPos crop) {
        ItemStack meal = firstStack(Items.BONE_MEAL);
        return !meal.isEmpty() && BoneMealItem.applyBonemeal(meal, level, crop, null);
    }

    boolean pick(BlockPos pos, CropKind kind) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        if (kind == CropKind.SWEET_BERRY_BUSH && block instanceof SweetBerryBushBlock) {
            int age = state.getValue(SweetBerryBushBlock.AGE);
            if (age < 2) {
                return false;
            }
            int count = age == 3 ? 3 : 2;
            ItemStack drop = new ItemStack(Items.SWEET_BERRIES, count);
            BlockState aged = state.setValue(SweetBerryBushBlock.AGE, 1);
            level.setBlock(pos, aged, Block.UPDATE_ALL);
            ItemEntity entity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop);
            entity.setPickUpDelay(0);
            level.addFreshEntity(entity);
            return true;
        }
        return false;
    }

    void rescueDrops(BlockPos broken) {
        AABB area = new AABB(broken.getX(), broken.getY(), broken.getZ(),
                broken.getX() + 1.0, broken.getY() + 1.0, broken.getZ() + 1.0).inflate(1.0);
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, area)) {
            drop.setPos(spirit.getX(), spirit.getY(), spirit.getZ());
            drop.setDeltaMovement(Vec3.ZERO);
        }
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
