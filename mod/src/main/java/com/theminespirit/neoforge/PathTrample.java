package com.theminespirit.neoforge;

import com.theminespirit.core.board.ActionToolPlans;
import com.theminespirit.core.building.PathGround;
import com.theminespirit.core.building.SchematicActions;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

final class PathTrample {
    private static final int NO_SLOT = -1;
    private static final double TOP_FACE_OFFSET = 0.5;

    private PathTrample() {}

    static BlockPlacer.Begin trample(SpiritEntity spirit, BlockPos target) {
        Level level = spirit.level();
        String current = BlockStates.idOf(level.getBlockState(target));
        boolean trample = SchematicActions.isTrampleGround(current);
        boolean air = PathGround.airCell(current);
        boolean raised =
                !trample && PathGround.raisedPath(current, BlockStates.idOf(level.getBlockState(target.below())));
        if (!trample && !raised && !air) {
            return new BlockPlacer.Refused(
                    BlockPlacer.RefuseReason.NO_SUPPORT,
                    "тропинку топчут по земле или траве, а в " + target.toShortString() + " стоит " + current);
        }
        int slot = shovelSlot(spirit);
        if (slot == NO_SLOT) {
            return new BlockPlacer.Refused(
                    BlockPlacer.RefuseReason.NO_MATERIAL, "нет лопаты для тропинки: сначала нужна лопата");
        }
        ItemStack shovel = spirit.getInventory().getItem(slot);
        if (air && !raised) {
            BlockPos below = target.below();
            if (!level.getBlockState(below).canBeReplaced()) {
                return new BlockPlacer.Refused(
                        BlockPlacer.RefuseReason.NO_SUPPORT,
                        "под пустой клеткой " + target.toShortString() + " нет опоры: дорожка повиснет в воздухе");
            }
            if (spirit.getInventory().countItem(Items.DIRT) < 1) {
                return new BlockPlacer.Refused(
                        BlockPlacer.RefuseReason.NO_MATERIAL,
                        "нет земли под дорожку в " + target.toShortString() + ": снизу пусто");
            }
            spirit.getInventory().removeItemType(Items.DIRT, 1);
            level.setBlock(below, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(target, Blocks.DIRT_PATH.defaultBlockState(), Block.UPDATE_ALL);
            SpiritGesture.workOn(spirit, target);
            shovel.hurtAndBreak(1, spirit, EquipmentSlot.MAINHAND);
            if (level instanceof ServerLevel server) {
                PlacedBlocksData.get(server).markSpirit(target);
            }
            return new BlockPlacer.Started(target.immutable());
        }
        BlockPos ground = raised ? target.below() : target;
        UseOnContext context = new UseOnContext(level, null, InteractionHand.MAIN_HAND, shovel, topFace(ground));
        InteractionResult result = shovel.getItem().useOn(context);
        if (!result.consumesAction() || !level.getBlockState(ground).is(Blocks.DIRT_PATH)) {
            return new BlockPlacer.Refused(
                    BlockPlacer.RefuseReason.NO_SUPPORT,
                    "лопатой по " + ground.toShortString()
                            + " тропинка не вышла: нужна земля или трава под открытым небом");
        }
        SpiritGesture.workOn(spirit, ground);
        shovel.hurtAndBreak(1, spirit, EquipmentSlot.MAINHAND);
        if (level instanceof ServerLevel server) {
            PlacedBlocksData.get(server).markSpirit(ground);
        }
        return new BlockPlacer.Started(ground.immutable());
    }

    static Optional<TaskNode> toolRestock(SpiritEntity spirit, String blockId) {
        String kind = SchematicActions.toolKind(blockId);
        if (kind == null) {
            return Optional.empty();
        }
        if ("*_hoe".equals(kind)) {
            return hasHoe(spirit) ? Optional.empty() : Optional.of(ActionToolPlans.hoe());
        }
        return shovelSlot(spirit) == NO_SLOT ? Optional.of(ActionToolPlans.shovel()) : Optional.empty();
    }

    static boolean hasTool(SpiritEntity spirit, String blockId) {
        String kind = SchematicActions.toolKind(blockId);
        if (kind == null) {
            return true;
        }
        return "*_hoe".equals(kind) ? hasHoe(spirit) : shovelSlot(spirit) != NO_SLOT;
    }

    private static boolean hasHoe(SpiritEntity spirit) {
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (stack.getItem() instanceof HoeItem && stack.getDamageValue() < stack.getMaxDamage() - 1) {
                return true;
            }
        }
        return false;
    }

    private static int shovelSlot(SpiritEntity spirit) {
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (stack.getItem() instanceof ShovelItem && stack.getDamageValue() < stack.getMaxDamage() - 1) {
                return slot;
            }
        }
        return NO_SLOT;
    }

    private static BlockHitResult topFace(BlockPos ground) {
        return new BlockHitResult(Vec3.atCenterOf(ground).add(0, TOP_FACE_OFFSET, 0), Direction.UP, ground, false);
    }
}
