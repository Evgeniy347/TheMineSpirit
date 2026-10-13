package com.theminespirit.gametest;

import static com.theminespirit.gametest.CraftTestSupport.assertCount;
import static com.theminespirit.gametest.CraftTestSupport.assertFinished;
import static com.theminespirit.gametest.CraftTestSupport.assertReason;
import static com.theminespirit.gametest.CraftTestSupport.cleanup;
import static com.theminespirit.gametest.CraftTestSupport.field;
import static com.theminespirit.gametest.CraftTestSupport.prepare;
import static com.theminespirit.gametest.CraftTestSupport.speedUpFurnaces;
import static com.theminespirit.gametest.CraftTestSupport.stack;
import static com.theminespirit.gametest.FullCycleScene.TABLE;
import static com.theminespirit.gametest.FullCycleScene.TREE;
import static com.theminespirit.gametest.FullCycleScene.TREE_LOGS;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.PlanParser;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.SpiritScripts;
import com.theminespirit.neoforge.inventory.ContainerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AcquireGameTests {
    private static final int SETTLE_TICKS = 1;
    private static final int WOOD_TICKS_LIMIT = 400;
    private static final int STONE_TICKS_LIMIT = 500;
    private static final int IRON_TICKS_LIMIT = 700;
    private static final int COBBLESTONE_FOR_FURNACE = 3;
    private static final int IRON_ORE_COUNT = 3;

    private AcquireGameTests() {
    }

    @GameTest(template = "arena", batch = "acquire_wood", timeoutTicks = WOOD_TICKS_LIMIT)
    public static void acquireWoodenPickaxe_FromScratch_PickaxeInInventory(GameTestHelper helper) {
        field(helper);
        FullCycleScene.buildOakTree(helper, TREE, TREE_LOGS);
        helper.setBlock(TABLE, Blocks.CRAFTING_TABLE);
        SpiritEntity spirit = prepare(helper);
        TaskNode plan = PlanParser.parse("""
                {"type":"script","script":"acquire","params":{"item":"wooden_pickaxe","count":1}}
                """, SpiritScripts.catalog());
        SpiritRun run = SpiritRun.launch(helper, spirit, plan, SETTLE_TICKS);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            assertCount(helper, spirit, Items.WOODEN_PICKAXE, 1, "деревянная кирка");
            helper.assertTrue(FullCycleScene.countBlocks(helper, Blocks.OAK_LOG) == 0,
                    "дерево обязано быть израсходовано: осталось "
                            + FullCycleScene.countBlocks(helper, Blocks.OAK_LOG));
            cleanup(helper, spirit);
        });
    }

    @GameTest(template = "arena", batch = "acquire_stone", timeoutTicks = STONE_TICKS_LIMIT)
    public static void acquireStonePickaxe_FromScratch_PickaxeInInventory(GameTestHelper helper) {
        field(helper);
        FullCycleScene.buildOakTree(helper, TREE, TREE_LOGS);
        FullCycleScene.placeCobblestoneRow(helper, COBBLESTONE_FOR_FURNACE);
        helper.setBlock(TABLE, Blocks.CRAFTING_TABLE);
        SpiritEntity spirit = prepare(helper, stack(Items.WOODEN_PICKAXE, 1));
        TaskNode plan = PlanParser.parse("""
                {"type":"script","script":"acquire","params":{"item":"stone_pickaxe","count":1}}
                """, SpiritScripts.catalog());
        SpiritRun run = SpiritRun.launch(helper, spirit, plan, SETTLE_TICKS);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            assertCount(helper, spirit, Items.STONE_PICKAXE, 1, "каменная кирка");
            helper.assertTrue(FullCycleScene.countBlocks(helper, Blocks.COBBLESTONE) == 0,
                    "булыжник обязан быть израсходован: осталось "
                            + FullCycleScene.countBlocks(helper, Blocks.COBBLESTONE));
            cleanup(helper, spirit);
        });
    }

    // [флак] добыча железной руды и плавка; замерено ~340 тиков духа, запас 2×
    @GameTest(template = "arena", batch = "acquire_iron", timeoutTicks = IRON_TICKS_LIMIT)
    public static void acquireIronIngot_FromScratch_IngotInInventory(GameTestHelper helper) {
        field(helper);
        placeIronOreRow(helper);
        helper.setBlock(5, 1, 5, Blocks.FURNACE);
        SpiritEntity spirit = prepare(helper, stack(Items.STONE_PICKAXE, 1), stack(Items.COAL, 2));
        TaskNode plan = PlanParser.parse("""
                {"type":"script","script":"acquire","params":{"item":"iron_ingot","count":1}}
                """, SpiritScripts.catalog());
        SpiritRun run = SpiritRun.launch(helper, spirit, plan, SETTLE_TICKS);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            assertCount(helper, spirit, Items.IRON_INGOT, 1, "железный слиток");
            helper.assertTrue(FullCycleScene.countBlocks(helper, Blocks.IRON_ORE) == 0,
                    "железная руда обязана быть израсходована: осталось "
                            + FullCycleScene.countBlocks(helper, Blocks.IRON_ORE));
            cleanup(helper, spirit);
        });
    }

    @GameTest(template = "arena", batch = "acquire_iron", timeoutTicks = IRON_TICKS_LIMIT)
    public static void acquireIronIngot_DeepslateNearby_GathersIronOre(GameTestHelper helper) {
        field(helper);
        placeIronOreRow(helper);
        for (int x = 0; x < IRON_ORE_COUNT; x++) {
            helper.setBlock(3 + x, 1, 6, Blocks.DEEPSLATE_IRON_ORE);
        }
        helper.setBlock(5, 1, 5, Blocks.FURNACE);
        SpiritEntity spirit = prepare(helper, stack(Items.STONE_PICKAXE, 1), stack(Items.COAL, 2));
        TaskNode plan = PlanParser.parse("""
                {"type":"script","script":"acquire","params":{"item":"iron_ingot","count":1}}
                """, SpiritScripts.catalog());
        SpiritRun run = SpiritRun.launch(helper, spirit, plan, SETTLE_TICKS);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            assertCount(helper, spirit, Items.IRON_INGOT, 1, "железный слиток");
            helper.assertTrue(FullCycleScene.countBlocks(helper, Blocks.DEEPSLATE_IRON_ORE) == IRON_ORE_COUNT,
                    "deepslate_iron_ore обязан остаться нетронутым: осталось "
                            + FullCycleScene.countBlocks(helper, Blocks.DEEPSLATE_IRON_ORE));
            cleanup(helper, spirit);
        });
    }

    @GameTest(template = "arena", batch = "acquire_have", timeoutTicks = 100)
    public static void acquireWoodenPickaxe_AlreadyInInventory_SucceedsWithoutWorld(GameTestHelper helper) {
        field(helper);
        FullCycleScene.buildOakTree(helper, TREE, TREE_LOGS);
        SpiritEntity spirit = prepare(helper, stack(Items.WOODEN_PICKAXE, 1));
        TaskNode plan = PlanParser.parse("""
                {"type":"script","script":"acquire","params":{"item":"wooden_pickaxe","count":1}}
                """, SpiritScripts.catalog());
        SpiritRun run = SpiritRun.launch(helper, spirit, plan, SETTLE_TICKS);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            assertCount(helper, spirit, Items.WOODEN_PICKAXE, 1, "деревянная кирка");
            helper.assertTrue(FullCycleScene.countBlocks(helper, Blocks.OAK_LOG) == TREE_LOGS,
                    "дерево обязано остаться нетронутым: осталось "
                            + FullCycleScene.countBlocks(helper, Blocks.OAK_LOG));
            helper.assertTrue(run.spiritTicks() <= 2,
                    "acquire с готовым предметом обязан завершиться за ≤ 2 тика, заняло " + run.spiritTicks());
            cleanup(helper, spirit);
        });
    }

    @GameTest(template = "arena", batch = "acquire_chest", timeoutTicks = 100)
    public static void acquireWoodenPickaxe_InNearChest_WithdrawsWithoutGathering(GameTestHelper helper) {
        field(helper);
        helper.setBlock(6, 1, 4, Blocks.CHEST);
        fillChest(helper, new BlockPos(6, 1, 4), Items.WOODEN_PICKAXE, 1);
        SpiritEntity spirit = prepare(helper);
        TaskNode plan = PlanParser.parse("""
                {"type":"script","script":"acquire","params":{"item":"wooden_pickaxe","count":1}}
                """, SpiritScripts.catalog());
        SpiritRun run = SpiritRun.launch(helper, spirit, plan, SETTLE_TICKS);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            assertCount(helper, spirit, Items.WOODEN_PICKAXE, 1, "деревянная кирка из сундука");
            assertChestEmpty(helper, new BlockPos(6, 1, 4), Items.WOODEN_PICKAXE);
            cleanup(helper, spirit);
        });
    }

    @GameTest(template = "arena", batch = "acquire_fail", timeoutTicks = 200)
    public static void acquireWoodenPickaxe_NoSource_FailedWithNumbers(GameTestHelper helper) {
        field(helper);
        SpiritEntity spirit = prepare(helper);
        TaskNode plan = PlanParser.parse("""
                {"type":"script","script":"acquire","params":{"item":"wooden_pickaxe","count":1}}
                """, SpiritScripts.catalog());
        SpiritRun run = SpiritRun.launch(helper, spirit, plan, SETTLE_TICKS);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.FAILED);
            assertReason(helper, spirit, "wooden_pickaxe");
            assertReason(helper, spirit, "попыток");
            cleanup(helper, spirit);
        });
    }

    @GameTest(template = "arena", batch = "acquire_restart", timeoutTicks = WOOD_TICKS_LIMIT)
    public static void acquireWoodenPickaxe_RestartedMidway_DoesNotGatherTwice(GameTestHelper helper) {
        field(helper);
        FullCycleScene.buildOakTree(helper, TREE, TREE_LOGS);
        helper.setBlock(TABLE, Blocks.CRAFTING_TABLE);
        SpiritEntity spirit = prepare(helper);
        spirit.startTask(PlanParser.parse("""
                {"type":"script","script":"acquire","params":{"item":"wooden_pickaxe","count":1}}
                """, SpiritScripts.catalog()));

        helper.runAfterDelay(5, () -> {
            CompoundTag tag = new CompoundTag();
            spirit.saveWithoutId(tag);
            SpiritEntity reloaded = CraftTestSupport.reload(helper, spirit);
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(reloaded.taskStatus().orElse(null) == RunStatus.RUNNING,
                        "восстановленный acquire обязан продолжить: статус "
                                + reloaded.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            });
            helper.succeedWhen(() -> {
                RunStatus status = reloaded.taskStatus().orElse(null);
                helper.assertTrue(status == RunStatus.SUCCEEDED,
                        "задача обязана дойти до SUCCEEDED после перезапуска, статус: "
                                + (status == null ? "нет задачи" : status)
                                + ", причина: " + reloaded.taskFailureReason().orElse("нет"));
                assertCount(helper, reloaded, Items.WOODEN_PICKAXE, 1, "деревянная кирка после перезапуска");
                helper.assertTrue(FullCycleScene.countBlocks(helper, Blocks.OAK_LOG) == 0,
                        "дерево обязано быть израсходовано один раз: осталось "
                                + FullCycleScene.countBlocks(helper, Blocks.OAK_LOG));
                cleanup(helper, reloaded);
            });
        });
    }

    private static void placeIronOreRow(GameTestHelper helper) {
        for (int x = 0; x < IRON_ORE_COUNT; x++) {
            helper.setBlock(3 + x, 1, 3, Blocks.IRON_ORE);
        }
    }

    private static void placeCobblestoneNear(GameTestHelper helper) {
        for (int x = 0; x < COBBLESTONE_FOR_FURNACE; x++) {
            helper.setBlock(3 + x, 1, 4, Blocks.COBBLESTONE);
        }
    }

    private static void fillChest(GameTestHelper helper, BlockPos pos, Item item, int count) {
        ContainerAccess.Open open = ContainerAccess.open(helper.getLevel(), helper.absolutePos(pos));
        if (open instanceof ContainerAccess.Opened opened) {
            opened.container().setItem(0, new ItemStack(item, count));
        }
    }

    private static void assertChestEmpty(GameTestHelper helper, BlockPos pos, Item item) {
        ContainerAccess.Open open = ContainerAccess.open(helper.getLevel(), helper.absolutePos(pos));
        if (open instanceof ContainerAccess.Opened opened) {
            int count = GameTestFixtures.countInInventory(opened.container(), item);
            helper.assertTrue(count == 0, "сундук обязан отдать предмет: осталось " + count);
        }
    }
}
