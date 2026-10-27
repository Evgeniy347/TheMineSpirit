package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.inventory.ContainerAccess;
import com.theminespirit.neoforge.inventory.InventoryTables;
import com.theminespirit.neoforge.inventory.StackMapper;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
final class InventorySceneSupport {
    private InventorySceneSupport() {}

    static void placeDoubleChest(GameTestHelper helper, int westX, int eastX, int y, int z) {
        BlockPos west = helper.absolutePos(new BlockPos(westX, y, z));
        BlockPos east = helper.absolutePos(new BlockPos(eastX, y, z));
        helper.getLevel()
                .setBlock(
                        west,
                        Blocks.CHEST
                                .defaultBlockState()
                                .setValue(ChestBlock.FACING, Direction.NORTH)
                                .setValue(ChestBlock.TYPE, ChestType.LEFT),
                        3);
        helper.getLevel()
                .setBlock(
                        east,
                        Blocks.CHEST
                                .defaultBlockState()
                                .setValue(ChestBlock.FACING, Direction.NORTH)
                                .setValue(ChestBlock.TYPE, ChestType.RIGHT),
                        3);
    }

    static Container accessChest(GameTestHelper helper) {
        ContainerAccess.Open open = ContainerAccess.open(helper.getLevel(), helper.absolutePos(new BlockPos(6, 1, 4)));
        if (!(open instanceof ContainerAccess.Opened opened)) {
            throw new IllegalStateException("Тестовый сундук не открылся: " + describe(open));
        }
        return opened.container();
    }

    static int countAll(Container container) {
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            total += container.getItem(slot).getCount();
        }
        return total;
    }

    static int countStored(SpiritEntity spirit, Item item) {
        ItemStack hand = spirit.getMainHandItem();
        return GameTestFixtures.countInInventory(spirit.getInventory(), item) + (hand.is(item) ? hand.getCount() : 0);
    }

    static Container chestAt(GameTestHelper helper, int x, int y, int z) {
        ContainerAccess.Open open = ContainerAccess.open(helper.getLevel(), helper.absolutePos(new BlockPos(x, y, z)));
        if (open instanceof ContainerAccess.Opened opened) {
            return opened.container();
        }
        throw new IllegalStateException(
                "Тестовый сундук (" + x + "," + y + "," + z + ") не открылся: " + describe(open));
    }

    static List<String> snapshotInventory(SpiritEntity spirit) {
        List<String> slots = new ArrayList<>();
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            slots.add(stack.isEmpty() ? "empty" : StackMapper.itemId(stack) + "x" + stack.getCount());
        }
        return slots;
    }

    static int chestCount(GameTestHelper helper, Item item) {
        int total = 0;
        Container chest = accessChest(helper);
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            if (chest.getItem(slot).is(item)) {
                total += chest.getItem(slot).getCount();
            }
        }
        return total;
    }

    static List<String> snapshotChest(GameTestHelper helper) {
        List<String> slots = new ArrayList<>();
        Container chest = accessChest(helper);
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (stack.isEmpty()) {
                slots.add("empty");
            } else {
                slots.add(StackMapper.itemId(stack) + "x" + stack.getCount() + "d" + stack.getDamageValue());
            }
        }
        return slots;
    }

    static int pickaxeStacks(GameTestHelper helper) {
        return itemStacks(helper, Items.IRON_PICKAXE);
    }

    static int itemStacks(GameTestHelper helper, Item item) {
        Container chest = accessChest(helper);
        int stacks = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            if (chest.getItem(slot).is(item)) {
                stacks++;
            }
        }
        return stacks;
    }

    static String coordsOf(BlockPos absolute) {
        return absolute.getX() + "," + absolute.getY() + "," + absolute.getZ();
    }

    static List<String> exactMatches() {
        List<String> exact = new ArrayList<>();
        InventoryTables.values()
                .rules()
                .forEach(rule -> rule.match().stream()
                        .filter(match -> !IdFilter.isPattern(match))
                        .forEach(exact::add));
        InventoryTables.kit()
                .rules()
                .forEach(rule -> rule.match().stream()
                        .filter(match -> !IdFilter.isPattern(match))
                        .forEach(exact::add));
        InventoryTables.kit().neverGiveAway().stream()
                .filter(match -> !IdFilter.isPattern(match))
                .forEach(exact::add);
        return exact;
    }

    static String describe(ContainerAccess.Open open) {
        return switch (open) {
            case ContainerAccess.Opened opened ->
                "открыт контейнер на " + opened.container().getContainerSize() + " слотов";
            case ContainerAccess.Refused refused -> "отказ: " + refused.reason();
        };
    }
}
