package com.theminespirit.neoforge.husbandry;

import com.theminespirit.core.board.HiveRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.state.BlockState;

final class HiveHarvest {
    private final HiveSession session;

    HiveHarvest(HiveSession session) {
        this.session = session;
    }

    void harvest() {
        BlockState state = session.level.getBlockState(session.hive);
        if (!fullHive(state)) {
            session.resetTarget();
            return;
        }
        HiveRules.Tool tool = HiveRules.chooseTool(
                session.spirit.getInventory().countItem(Items.SHEARS) > 0,
                session.spirit.getInventory().countItem(Items.GLASS_BOTTLE));
        if (tool == HiveRules.Tool.NONE) {
            session.fail("нечем собирать улей " + session.hive.toShortString()
                    + ": нужны ножницы или стеклянная бутылка, мир не изменён");
            return;
        }
        if (tool == HiveRules.Tool.SHEARS) {
            shears(session.hive);
        } else {
            bottle(session.hive);
        }
    }

    private void shears(BlockPos pos) {
        ItemStack shears = firstStack(Items.SHEARS);
        if (shears.isEmpty()) {
            session.fail("ножницы исчезли из инвентаря: улей " + pos.toShortString() + " не тронут");
            return;
        }
        if (!session.spirit.getInventory().canAddItem(new ItemStack(Items.HONEYCOMB, 3))) {
            session.fail("инвентарь полон: не помещаются 3 соты, улей " + pos.toShortString() + " не тронут");
            return;
        }
        session.spirit.getInventory().addItem(new ItemStack(Items.HONEYCOMB, 3));
        shears.hurtAndBreak(1, session.spirit, EquipmentSlot.MAINHAND);
        resetHoneyLevel(pos);
        session.done++;
        session.resetTarget();
    }

    private void bottle(BlockPos pos) {
        if (session.spirit.getInventory().countItem(Items.GLASS_BOTTLE) < 1) {
            session.fail("стеклянная бутылка исчезла из инвентаря: улей " + pos.toShortString() + " не тронут");
            return;
        }
        if (!session.spirit.getInventory().canAddItem(new ItemStack(Items.HONEY_BOTTLE))) {
            session.fail("инвентарь полон: не помещается бутылка мёда, улей " + pos.toShortString() + " не тронут");
            return;
        }
        session.spirit.getInventory().removeItemType(Items.GLASS_BOTTLE, 1);
        session.spirit.getInventory().addItem(new ItemStack(Items.HONEY_BOTTLE));
        resetHoneyLevel(pos);
        session.done++;
        session.resetTarget();
    }

    private void resetHoneyLevel(BlockPos pos) {
        BlockState current = session.level.getBlockState(pos);
        if (current.hasProperty(BeehiveBlock.HONEY_LEVEL)) {
            session.level.setBlock(pos, current.setValue(BeehiveBlock.HONEY_LEVEL, 0), 3);
        }
    }

    BlockPos nearestFullHive() {
        BlockPos origin = session.spirit.blockPosition();
        BlockPos found = null;
        double best = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-session.searchRadius, -session.searchRadius, -session.searchRadius),
                origin.offset(session.searchRadius, session.searchRadius, session.searchRadius))) {
            if (!fullHive(session.level.getBlockState(pos))) {
                continue;
            }
            double distance = pos.distSqr(origin);
            if (distance < best) {
                best = distance;
                found = pos.immutable();
            }
        }
        return found;
    }

    static boolean fullHive(BlockState state) {
        return state.getBlock() instanceof BeehiveBlock
                && state.hasProperty(BeehiveBlock.HONEY_LEVEL)
                && HiveRules.fullHive(state.getValue(BeehiveBlock.HONEY_LEVEL));
    }

    private ItemStack firstStack(Item item) {
        for (int slot = 0; slot < session.spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = session.spirit.getInventory().getItem(slot);
            if (stack.is(item)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
