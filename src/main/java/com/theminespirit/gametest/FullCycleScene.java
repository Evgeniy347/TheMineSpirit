package com.theminespirit.gametest;

import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;

final class FullCycleScene {
    static final BlockPos TABLE = new BlockPos(5, 1, 1);
    static final BlockPos CHEST = new BlockPos(1, 1, 6);
    static final BlockPos TREE = new BlockPos(6, 1, 6);
    static final BlockPos COBBLESTONE_ROW = new BlockPos(0, 1, 8);
    static final int TREE_LOGS = 3;
    static final int INGOTS_BEFORE = 7;
    private static final int SCENE_HEIGHT = 8;
    private static final int DIRT_IN_CHEST = 10;
    private static final int INGOTS_IN_FIRST_STACK = 3;
    private static final int INGOTS_IN_SECOND_STACK = 4;
    private static final int SLOT_OF_DIRT = 0;
    private static final int SLOT_OF_FIRST_INGOTS = 5;
    private static final int SLOT_OF_SECOND_INGOTS = 9;

    private FullCycleScene() {}

    static void buildOakTree(GameTestHelper helper, BlockPos base, int trunk) {
        helper.setBlock(base.below(), Blocks.DIRT);
        for (int y = 0; y < trunk; y++) {
            helper.setBlock(base.above(y), Blocks.OAK_LOG);
        }
        var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false);
        BlockPos top = base.above(trunk - 1);
        helper.setBlock(top.above(), leaves);
        helper.setBlock(top.east(), leaves);
        helper.setBlock(top.west(), leaves);
        helper.setBlock(top.north(), leaves);
        helper.setBlock(top.south(), leaves);
    }

    static void placeCobblestoneRow(GameTestHelper helper, int count) {
        for (int x = 0; x < count; x++) {
            helper.setBlock(COBBLESTONE_ROW.offset(x, 0, 0), Blocks.COBBLESTONE);
        }
    }

    static void placeUnsortedChest(GameTestHelper helper) {
        helper.setBlock(CHEST, Blocks.CHEST);
        Container chest = chest(helper);
        chest.setItem(SLOT_OF_DIRT, new ItemStack(Items.DIRT, DIRT_IN_CHEST));
        chest.setItem(SLOT_OF_FIRST_INGOTS, new ItemStack(Items.IRON_INGOT, INGOTS_IN_FIRST_STACK));
        chest.setItem(SLOT_OF_SECOND_INGOTS, new ItemStack(Items.IRON_INGOT, INGOTS_IN_SECOND_STACK));
    }

    static Container chest(GameTestHelper helper) {
        if (helper.getBlockEntity(CHEST) instanceof Container container) {
            return container;
        }
        throw new IllegalStateException("В клетке сундука " + CHEST + " нет контейнера: сцена не построена");
    }

    static String chestTarget(GameTestHelper helper) {
        BlockPos absolute = helper.absolutePos(CHEST);
        return absolute.getX() + "," + absolute.getY() + "," + absolute.getZ();
    }

    static void assertChestHolds(GameTestHelper helper, Item item, int expected) {
        int actual = GameTestFixtures.countInInventory(chest(helper), item);
        helper.assertTrue(
                actual == expected, "в сундуке ожидалось " + expected + " " + item + ", фактически " + actual);
    }

    static void assertChestSortedAfterDeposit(GameTestHelper helper, Item deposited) {
        Container chest = chest(helper);
        List<Item> kinds = new ArrayList<>();
        int filled = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            helper.assertTrue(
                    slot == filled,
                    "сундук обязан быть отсортирован без дырок между стопками: слот " + slot + " занят, а подряд "
                            + "заполнено только " + filled);
            helper.assertTrue(
                    !kinds.contains(stack.getItem()),
                    "сундук обязан быть отсортирован: стопки одного предмета слиты, а " + stack.getItem()
                            + " лежит в двух слотах");
            kinds.add(stack.getItem());
            filled++;
        }
        helper.assertTrue(
                kinds.contains(deposited) && kinds.contains(Items.IRON_INGOT) && kinds.contains(Items.DIRT),
                "старое содержимое сундука и положенное духом обязано остаться: " + kinds);
    }

    static int countBlocks(GameTestHelper helper, Block block) {
        int total = 0;
        for (int x = 0; x < GameTestFixtures.FLOOR_SIZE; x++) {
            for (int y = 1; y < SCENE_HEIGHT; y++) {
                for (int z = 0; z < GameTestFixtures.FLOOR_SIZE; z++) {
                    if (helper.getBlockState(new BlockPos(x, y, z)).is(block)) {
                        total++;
                    }
                }
            }
        }
        return total;
    }

    static int ownedBy(GameTestHelper helper, Item item, Container... containers) {
        int total = GameTestFixtures.countInWorld(helper, item);
        for (Container container : containers) {
            total += GameTestFixtures.countInInventory(container, item);
        }
        return total;
    }

    static List<String> scriptsOf(TaskNode plan) {
        List<String> scripts = new ArrayList<>();
        collect(plan, scripts);
        return scripts;
    }

    private static void collect(TaskNode node, List<String> scripts) {
        switch (node) {
            case ScriptCall call ->
                scripts.add(call.script() + ":"
                        + call.params().getOrDefault("item", call.params().get("block")));
            case SequenceNode sequence -> sequence.steps().forEach(step -> collect(step, scripts));
            default ->
                throw new IllegalArgumentException(
                        "Резолвер выдал узел " + node.getClass().getSimpleName()
                                + ", а тест разбирает только sequence и script: расширь разбор плана");
        }
    }
}
