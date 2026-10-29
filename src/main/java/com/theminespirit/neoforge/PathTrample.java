package com.theminespirit.neoforge;

import com.theminespirit.core.building.SchematicActions;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
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
        if (!SchematicActions.isTrampleGround(current)) {
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
        UseOnContext context = new UseOnContext(level, null, InteractionHand.MAIN_HAND, shovel, topFace(target));
        InteractionResult result = shovel.getItem().useOn(context);
        if (!result.consumesAction() || !level.getBlockState(target).is(Blocks.DIRT_PATH)) {
            return new BlockPlacer.Refused(
                    BlockPlacer.RefuseReason.NO_SUPPORT,
                    "лопатой по " + target.toShortString()
                            + " тропинка не вышла: нужна земля или трава под открытым небом");
        }
        SpiritGesture.workOn(spirit, target);
        shovel.hurtAndBreak(1, spirit, EquipmentSlot.MAINHAND);
        if (level instanceof ServerLevel server) {
            PlacedBlocksData.get(server).markSpirit(target);
        }
        return new BlockPlacer.Started(target.immutable());
    }

    static List<ScriptCall> restock(SpiritEntity spirit, String blockId) {
        if (!SchematicActions.isActionPlaced(blockId) || shovelSlot(spirit) != NO_SLOT) {
            return List.of();
        }
        boolean cobble = spirit.getInventory().countItem(Items.COBBLESTONE) > 0;
        String item = SchematicActions.shovelItem(cobble);
        int held = spirit.getInventory().countItem(BuiltInRegistries.ITEM.get(ResourceLocation.parse(item)));
        return List.of(new ScriptCall("acquire", Map.of("item", item, "count", held + 1)));
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
