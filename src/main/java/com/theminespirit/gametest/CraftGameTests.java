package com.theminespirit.gametest;

import static com.theminespirit.gametest.CraftTestSupport.FIELD;
import static com.theminespirit.gametest.CraftTestSupport.TIMEOUT;
import static com.theminespirit.gametest.CraftTestSupport.assertCount;
import static com.theminespirit.gametest.CraftTestSupport.assertFinished;
import static com.theminespirit.gametest.CraftTestSupport.assertReason;
import static com.theminespirit.gametest.CraftTestSupport.cleanup;
import static com.theminespirit.gametest.CraftTestSupport.field;
import static com.theminespirit.gametest.CraftTestSupport.launch;
import static com.theminespirit.gametest.CraftTestSupport.prepare;
import static com.theminespirit.gametest.CraftTestSupport.reload;
import static com.theminespirit.gametest.CraftTestSupport.stack;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.building.SpiritReach;
import com.theminespirit.core.crafting.CraftTuning;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CraftGameTests {
    private static final BlockPos TABLE_FAR = new BlockPos(5, 1, 1);
    private static final int PLAYER_SLOTS = 36;

    private CraftGameTests() {}

    @GameTest(template = FIELD, batch = "craft_planks", timeoutTicks = TIMEOUT)
    public static void craft_TwoLogsEightPlanks_ConsumesLogsExactly(GameTestHelper helper) {
        field(helper);
        SpiritEntity spirit = prepare(helper, stack(Items.OAK_LOG, 2));
        SpiritRun run = launch(helper, spirit, "craft", "oak_planks", 8);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            assertCount(helper, spirit, Items.OAK_PLANKS, 8, "доски из 2 брёвен (2 крафта по 4)");
            assertCount(helper, spirit, Items.OAK_LOG, 0, "брёвна после крафта 8 досок");
            cleanup(helper, spirit);
        });
    }

    @GameTest(template = FIELD, batch = "craft_sticks", timeoutTicks = TIMEOUT)
    public static void craft_FourSticksFromTwoPlanks_NoStationNeeded(GameTestHelper helper) {
        field(helper);
        SpiritEntity spirit = prepare(helper, stack(Items.OAK_PLANKS, 2));
        SpiritRun run = launch(helper, spirit, "craft", "stick", 4);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            assertCount(helper, spirit, Items.STICK, 4, "палки из двух досок без верстака");
            assertCount(helper, spirit, Items.OAK_PLANKS, 0, "доски после крафта палок");
            cleanup(helper, spirit);
        });
    }

    @GameTest(template = FIELD, batch = "craft_pickaxe_table_near", timeoutTicks = TIMEOUT)
    public static void craft_PickaxeWithTableFourBlocksAway_SpiritWalksUpAndCrafts(GameTestHelper helper) {
        field(helper);
        helper.setBlock(TABLE_FAR, Blocks.CRAFTING_TABLE);
        SpiritEntity spirit = prepare(helper, stack(Items.OAK_PLANKS, 3), stack(Items.STICK, 2));
        double startX = spirit.getX();
        SpiritRun run = launch(helper, spirit, "craft", "wooden_pickaxe", 1);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            assertCount(helper, spirit, Items.WOODEN_PICKAXE, 1, "кирка у верстака");
            assertCount(helper, spirit, Items.OAK_PLANKS, 0, "доски после крафта кирки");
            assertCount(helper, spirit, Items.STICK, 0, "палки после крафта кирки");
            BlockPos table = helper.absolutePos(TABLE_FAR);
            BlockPos feet = spirit.blockPosition();
            helper.assertTrue(
                    spirit.getX() > startX + 0.5
                            && SpiritReach.reachable(
                                    new BlockCoord(feet.getX(), feet.getY(), feet.getZ()),
                                    new BlockCoord(table.getX(), table.getY(), table.getZ())),
                    "дух обязан подойти к верстаку на досягаемость ("
                            + SpiritReach.current().describe()
                            + ", упрощение 44.2): стоит в " + feet.toShortString() + ", верстак "
                            + table.toShortString()
                            + ", сдвинулся по x на " + (spirit.getX() - startX));
            cleanup(helper, spirit);
        });
    }

    @GameTest(template = FIELD, batch = "craft_table_in_inventory", timeoutTicks = TIMEOUT)
    public static void craft_TableOnlyInInventory_PlacedOnFloorAndUsed(GameTestHelper helper) {
        field(helper);
        SpiritEntity spirit =
                prepare(helper, stack(Items.CRAFTING_TABLE, 1), stack(Items.OAK_PLANKS, 3), stack(Items.STICK, 2));
        SpiritRun run = launch(helper, spirit, "craft", "wooden_pickaxe", 1);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            assertCount(helper, spirit, Items.WOODEN_PICKAXE, 1, "кирка у поставленного верстака");
            assertCount(helper, spirit, Items.CRAFTING_TABLE, 0, "верстак в инвентаре после установки");
            helper.assertTrue(
                    tablesOnField(helper) == 1,
                    "дух обязан поставить ровно один верстак из инвентаря, на поле их " + tablesOnField(helper));
            cleanup(helper, spirit);
        });
    }

    // Печь первых шагов ставится сразу после крафта (place): в инвентаре крафтера её не видела плавка других духов
    // (приёмка «джунгли» 24.10: «нужна печь: рядом (16) нет, в инвентаре нет»).
    @GameTest(template = FIELD, batch = "craft_furnace_place", timeoutTicks = TIMEOUT)
    public static void craft_FurnaceWithPlace_FurnaceStandsOnField(GameTestHelper helper) {
        field(helper);
        SpiritEntity spirit = prepare(helper, stack(Items.CRAFTING_TABLE, 1), stack(Items.COBBLESTONE, 8));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall(
                        "craft",
                        Map.of("item", "furnace", "count", 1, "radius", CraftTestSupport.RADIUS, "place", true)),
                1);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            assertCount(helper, spirit, Items.FURNACE, 0, "печь в инвентаре после крафта с place");
            helper.assertTrue(
                    blocksOnField(helper, Blocks.FURNACE) == 1,
                    "дух обязан поставить скрафченную печь, на поле печей " + blocksOnField(helper, Blocks.FURNACE));
            cleanup(helper, spirit);
        });
    }

    // Сундуки склада первых шагов ставятся сразу после крафта (place), не вплотную друг к другу: в инвентаре крафтера
    // склада не было, сдача добытого не находила сундук (приёмка «джунгли» 24.10: «сундуки []»).
    @GameTest(template = FIELD, batch = "craft_chest_place", timeoutTicks = TIMEOUT)
    public static void craft_ChestsWithPlace_TwoSingleChestsOnField(GameTestHelper helper) {
        field(helper);
        SpiritEntity spirit = prepare(helper, stack(Items.CRAFTING_TABLE, 1), stack(Items.OAK_PLANKS, 16));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall(
                        "craft", Map.of("item", "chest", "count", 2, "radius", CraftTestSupport.RADIUS, "place", true)),
                1);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            assertCount(helper, spirit, Items.CHEST, 0, "сундуки в инвентаре после крафта с place");
            helper.assertTrue(
                    blocksOnField(helper, Blocks.CHEST) == 2,
                    "дух обязан поставить оба скрафченных сундука, на поле " + blocksOnField(helper, Blocks.CHEST));
            cleanup(helper, spirit);
        });
    }

    @GameTest(template = FIELD, batch = "craft_no_table", timeoutTicks = TIMEOUT)
    public static void craft_NoTableAnywhere_FailsBeforeSpendingMaterials(GameTestHelper helper) {
        field(helper);
        SpiritEntity spirit = prepare(helper, stack(Items.OAK_PLANKS, 3), stack(Items.STICK, 2));
        SpiritRun run = launch(helper, spirit, "craft", "wooden_pickaxe", 1);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.FAILED);
            assertReason(helper, spirit, "нужен верстак");
            assertCount(helper, spirit, Items.OAK_PLANKS, 3, "доски при отказе без верстака");
            assertCount(helper, spirit, Items.STICK, 2, "палки при отказе без верстака");
            assertCount(helper, spirit, Items.WOODEN_PICKAXE, 0, "кирка при отказе без верстака");
            cleanup(helper, spirit);
        });
    }

    @GameTest(template = FIELD, batch = "craft_shortage", timeoutTicks = TIMEOUT)
    public static void craft_NotEnoughPlanks_FailsNamingShortageWithoutSpending(GameTestHelper helper) {
        field(helper);
        helper.setBlock(TABLE_FAR, Blocks.CRAFTING_TABLE);
        SpiritEntity spirit = prepare(helper, stack(Items.OAK_PLANKS, 2), stack(Items.STICK, 2));
        SpiritRun run = launch(helper, spirit, "craft", "wooden_pickaxe", 1);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.FAILED);
            assertReason(helper, spirit, "не хватает: oak_planks 1 из 3");
            assertCount(helper, spirit, Items.OAK_PLANKS, 2, "доски при отказе по нехватке");
            assertCount(helper, spirit, Items.STICK, 2, "палки при отказе по нехватке");
            cleanup(helper, spirit);
        });
    }

    @GameTest(template = FIELD, batch = "craft_mixed_planks", timeoutTicks = TIMEOUT)
    public static void craft_MixedPlanks_PickaxeFromBirchAndOak(GameTestHelper helper) {
        field(helper);
        helper.setBlock(TABLE_FAR, Blocks.CRAFTING_TABLE);
        SpiritEntity spirit =
                prepare(helper, stack(Items.BIRCH_PLANKS, 1), stack(Items.OAK_PLANKS, 2), stack(Items.STICK, 2));
        SpiritRun run = launch(helper, spirit, "craft", "wooden_pickaxe", 1);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            assertCount(helper, spirit, Items.WOODEN_PICKAXE, 1, "кирка из смеси досок");
            assertCount(helper, spirit, Items.BIRCH_PLANKS, 0, "берёзовые доски после крафта");
            assertCount(helper, spirit, Items.OAK_PLANKS, 0, "дубовые доски после крафта");
            cleanup(helper, spirit);
        });
    }

    @GameTest(template = FIELD, batch = "craft_many_sticks", timeoutTicks = TIMEOUT)
    public static void craft_SixtyFourSticks_AtMostOneCraftPerTwoTicks(GameTestHelper helper) {
        field(helper);
        SpiritEntity spirit = prepare(helper, stack(Items.OAK_PLANKS, 32));
        SpiritRun run = launch(helper, spirit, "craft", "stick", 64);
        List<Integer> craftTicks = new ArrayList<>();
        int[] lastSticks = {0};
        helper.onEachTick(() -> {
            int sticks = GameTestFixtures.countInInventory(spirit.getInventory(), Items.STICK);
            if (sticks > lastSticks[0]) {
                craftTicks.add(spirit.tickCount);
                lastSticks[0] = sticks;
            }
        });

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            assertCount(helper, spirit, Items.STICK, 64, "палки после крафта 64 штук");
            assertCount(helper, spirit, Items.OAK_PLANKS, 0, "доски после крафта 64 палок");
            helper.assertTrue(
                    craftTicks.size() == 16,
                    "64 палки при выходе 4 — ровно 16 крафтов, зафиксировано " + craftTicks.size());
            for (int i = 1; i < craftTicks.size(); i++) {
                int gap = craftTicks.get(i) - craftTicks.get(i - 1);
                helper.assertTrue(
                        gap >= CraftTuning.CRAFT_INTERVAL_TICKS,
                        "между крафтами обязано пройти не меньше " + CraftTuning.CRAFT_INTERVAL_TICKS
                                + " тиков, между крафтами " + (i - 1) + " и " + i + " прошло " + gap);
            }
            cleanup(helper, spirit);
        });
    }

    @GameTest(template = FIELD, batch = "craft_reload", timeoutTicks = TIMEOUT)
    public static void craft_SpiritReloadedMidway_ContinuesWithRemainingCount(GameTestHelper helper) {
        field(helper);
        SpiritEntity spirit = prepare(helper, stack(Items.OAK_PLANKS, 32));
        launch(helper, spirit, "craft", "stick", 64);
        SpiritEntity[] current = {spirit};
        boolean[] reloaded = {false};
        helper.onEachTick(() -> {
            if (!reloaded[0] && GameTestFixtures.countInInventory(spirit.getInventory(), Items.STICK) >= 16) {
                current[0] = reload(helper, spirit);
                reloaded[0] = true;
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(reloaded[0], "перезагрузка духа ещё не выполнена");
            SpiritEntity now = current[0];
            helper.assertTrue(
                    now.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "восстановленный craft обязан завершиться успехом, статус: "
                            + now.taskStatus().orElse(null) + ", причина: "
                            + now.taskFailureReason().orElse("нет"));
            assertCount(helper, now, Items.STICK, 64, "палки после перезапуска: недостающее число, а не заново 64");
            assertCount(helper, now, Items.OAK_PLANKS, 0, "доски после перезапуска");
            cleanup(helper, now);
        });
    }

    @GameTest(template = FIELD, batch = "craft_cake", timeoutTicks = TIMEOUT)
    public static void craft_Cake_EmptyBucketsReturnToInventory(GameTestHelper helper) {
        field(helper);
        helper.setBlock(TABLE_FAR, Blocks.CRAFTING_TABLE);
        SpiritEntity spirit = prepare(
                helper, stack(Items.MILK_BUCKET, 3), stack(Items.SUGAR, 2), stack(Items.EGG, 1), stack(Items.WHEAT, 3));
        SpiritRun run = launch(helper, spirit, "craft", "cake", 1);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            assertCount(helper, spirit, Items.CAKE, 1, "торт");
            assertCount(helper, spirit, Items.BUCKET, 3, "пустые вёдра после торта");
            assertCount(helper, spirit, Items.MILK_BUCKET, 0, "вёдра с молоком после торта");
            cleanup(helper, spirit);
        });
    }

    @GameTest(template = FIELD, batch = "craft_full_inventory", timeoutTicks = TIMEOUT)
    public static void craft_NoRoomForResult_FailsWithoutSpending(GameTestHelper helper) {
        field(helper);
        SpiritEntity spirit = prepare(helper, stack(Items.OAK_LOG, 2));
        for (int slot = 1; slot < PLAYER_SLOTS; slot++) {
            spirit.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        SpiritRun run = launch(helper, spirit, "craft", "oak_planks", 4);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.FAILED);
            assertReason(helper, spirit, "инвентарь полон");
            assertCount(helper, spirit, Items.OAK_LOG, 2, "брёвна при отказе из-за полного инвентаря");
            assertCount(helper, spirit, Items.OAK_PLANKS, 0, "доски при отказе из-за полного инвентаря");
            cleanup(helper, spirit);
        });
    }

    @GameTest(template = FIELD, batch = "craft_dynamic", timeoutTicks = TIMEOUT)
    public static void craft_WrittenBook_FailsNoRecipe(GameTestHelper helper) {
        field(helper);
        SpiritEntity spirit = prepare(helper, stack(Items.BOOK, 1), stack(Items.INK_SAC, 1));
        SpiritRun run = launch(helper, spirit, "craft", "written_book", 1);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.FAILED);
            assertReason(helper, spirit, "нет рецепта");
            assertCount(helper, spirit, Items.BOOK, 1, "книга при отказе без рецепта");
            cleanup(helper, spirit);
        });
    }

    @GameTest(template = FIELD, batch = "craft_jungle_logs", timeoutTicks = TIMEOUT)
    public static void craft_JungleLogsToWoodenPickaxe_CraftsPlanksAndSticks(GameTestHelper helper) {
        craftPickaxeFromLogs(helper, stack(Items.JUNGLE_LOG, 2), Items.JUNGLE_LOG);
    }

    @GameTest(template = FIELD, batch = "craft_acacia_logs", timeoutTicks = TIMEOUT)
    public static void craft_AcaciaLogsToWoodenPickaxe_CraftsPlanksAndSticks(GameTestHelper helper) {
        craftPickaxeFromLogs(helper, stack(Items.ACACIA_LOG, 2), Items.ACACIA_LOG);
    }

    @GameTest(template = FIELD, batch = "craft_dark_oak_logs", timeoutTicks = TIMEOUT)
    public static void craft_DarkOakLogsToWoodenPickaxe_CraftsPlanksAndSticks(GameTestHelper helper) {
        craftPickaxeFromLogs(helper, stack(Items.DARK_OAK_LOG, 2), Items.DARK_OAK_LOG);
    }

    @GameTest(template = FIELD, batch = "craft_mangrove_logs", timeoutTicks = TIMEOUT)
    public static void craft_MangroveLogsToWoodenPickaxe_CraftsPlanksAndSticks(GameTestHelper helper) {
        craftPickaxeFromLogs(helper, stack(Items.MANGROVE_LOG, 2), Items.MANGROVE_LOG);
    }

    @GameTest(template = FIELD, batch = "craft_cherry_logs", timeoutTicks = TIMEOUT)
    public static void craft_CherryLogsToWoodenPickaxe_CraftsPlanksAndSticks(GameTestHelper helper) {
        craftPickaxeFromLogs(helper, stack(Items.CHERRY_LOG, 2), Items.CHERRY_LOG);
    }

    @GameTest(template = FIELD, batch = "craft_bamboo_blocks", timeoutTicks = TIMEOUT)
    public static void craft_BambooBlocksToWoodenPickaxe_CraftsPlanksAndSticks(GameTestHelper helper) {
        craftPickaxeFromLogs(helper, stack(Items.BAMBOO_BLOCK, 3), Items.BAMBOO_BLOCK);
    }

    private static void craftPickaxeFromLogs(GameTestHelper helper, ItemStack logs, Item logItem) {
        field(helper);
        helper.setBlock(TABLE_FAR, Blocks.CRAFTING_TABLE);
        SpiritEntity spirit = prepare(helper, logs);
        SpiritRun run = launch(helper, spirit, "craft", "wooden_pickaxe", 1);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            assertCount(helper, spirit, Items.WOODEN_PICKAXE, 1, "кирка из брёвен без готовых досок и палок");
            assertCount(helper, spirit, logItem, 0, "брёвна после цепочки досок и палок");
            cleanup(helper, spirit);
        });
    }

    private static int tablesOnField(GameTestHelper helper) {
        return blocksOnField(helper, Blocks.CRAFTING_TABLE);
    }

    private static int blocksOnField(GameTestHelper helper, Block block) {
        int tables = 0;
        for (int x = 0; x < GameTestFixtures.FLOOR_SIZE; x++) {
            for (int z = 0; z < GameTestFixtures.FLOOR_SIZE; z++) {
                if (helper.getBlockState(new BlockPos(x, 1, z)).is(block)) {
                    tables++;
                }
            }
        }
        return tables;
    }
}
