package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.crafting.CraftTuning;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
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
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CraftGameTests {
    private static final String FIELD = "arena";
    private static final BlockPos SPIRIT_START = new BlockPos(1, 1, 1);
    private static final BlockPos TABLE_FAR = new BlockPos(5, 1, 1);
    private static final int SETTLE_TICKS = 1;
    private static final int RADIUS = 16;
    private static final int TIMEOUT = 200;
    private static final int PLAYER_SLOTS = 36;

    private CraftGameTests() {
    }

    @GameTest(template = FIELD, batch = "craft_planks", timeoutTicks = TIMEOUT)
    public static void craft_TwoLogsEightPlanks_ConsumesLogsExactly(GameTestHelper helper) {
        field(helper);
        SpiritEntity spirit = prepare(helper, stack(Items.OAK_LOG, 2));
        SpiritRun run = launch(helper, spirit, "oak_planks", 8);

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
        SpiritRun run = launch(helper, spirit, "stick", 4);

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
        SpiritRun run = launch(helper, spirit, "wooden_pickaxe", 1);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            assertCount(helper, spirit, Items.WOODEN_PICKAXE, 1, "кирка у верстака");
            assertCount(helper, spirit, Items.OAK_PLANKS, 0, "доски после крафта кирки");
            assertCount(helper, spirit, Items.STICK, 0, "палки после крафта кирки");
            double distance = spirit.position().distanceTo(helper.absoluteVec(TABLE_FAR.getCenter()));
            helper.assertTrue(spirit.getX() > startX + 0.5 && distance <= CraftTuning.STATION_REACH + 0.2,
                    "дух обязан подойти к верстаку на " + CraftTuning.STATION_REACH + " блока и меньше, стоит в "
                            + distance + " от него, сдвинулся по x на " + (spirit.getX() - startX));
            cleanup(helper, spirit);
        });
    }

    @GameTest(template = FIELD, batch = "craft_table_in_inventory", timeoutTicks = TIMEOUT)
    public static void craft_TableOnlyInInventory_PlacedOnFloorAndUsed(GameTestHelper helper) {
        field(helper);
        SpiritEntity spirit = prepare(helper, stack(Items.CRAFTING_TABLE, 1), stack(Items.OAK_PLANKS, 3),
                stack(Items.STICK, 2));
        SpiritRun run = launch(helper, spirit, "wooden_pickaxe", 1);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            assertCount(helper, spirit, Items.WOODEN_PICKAXE, 1, "кирка у поставленного верстака");
            assertCount(helper, spirit, Items.CRAFTING_TABLE, 0, "верстак в инвентаре после установки");
            helper.assertTrue(tablesOnField(helper) == 1,
                    "дух обязан поставить ровно один верстак из инвентаря, на поле их " + tablesOnField(helper));
            cleanup(helper, spirit);
        });
    }

    @GameTest(template = FIELD, batch = "craft_no_table", timeoutTicks = TIMEOUT)
    public static void craft_NoTableAnywhere_FailsBeforeSpendingMaterials(GameTestHelper helper) {
        field(helper);
        SpiritEntity spirit = prepare(helper, stack(Items.OAK_PLANKS, 3), stack(Items.STICK, 2));
        SpiritRun run = launch(helper, spirit, "wooden_pickaxe", 1);

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
        SpiritRun run = launch(helper, spirit, "wooden_pickaxe", 1);

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
        SpiritEntity spirit = prepare(helper, stack(Items.BIRCH_PLANKS, 1), stack(Items.OAK_PLANKS, 2),
                stack(Items.STICK, 2));
        SpiritRun run = launch(helper, spirit, "wooden_pickaxe", 1);

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
        SpiritRun run = launch(helper, spirit, "stick", 64);
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
            helper.assertTrue(craftTicks.size() == 16, "64 палки при выходе 4 — ровно 16 крафтов, зафиксировано "
                    + craftTicks.size());
            for (int i = 1; i < craftTicks.size(); i++) {
                int gap = craftTicks.get(i) - craftTicks.get(i - 1);
                helper.assertTrue(gap >= CraftTuning.CRAFT_INTERVAL_TICKS,
                        "между крафтами обязано пройти не меньше " + CraftTuning.CRAFT_INTERVAL_TICKS
                                + " тиков, между крафтами " + (i - 1) + " и " + i + " прошло " + gap);
            }
            cleanup(helper, spirit);
        });
    }

    @GameTest(template = FIELD, batch = "craft_cake", timeoutTicks = TIMEOUT)
    public static void craft_Cake_EmptyBucketsReturnToInventory(GameTestHelper helper) {
        field(helper);
        helper.setBlock(TABLE_FAR, Blocks.CRAFTING_TABLE);
        SpiritEntity spirit = prepare(helper, stack(Items.MILK_BUCKET, 3), stack(Items.SUGAR, 2),
                stack(Items.EGG, 1), stack(Items.WHEAT, 3));
        SpiritRun run = launch(helper, spirit, "cake", 1);

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
        SpiritRun run = launch(helper, spirit, "oak_planks", 4);

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
        SpiritRun run = launch(helper, spirit, "written_book", 1);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.FAILED);
            assertReason(helper, spirit, "нет рецепта");
            assertCount(helper, spirit, Items.BOOK, 1, "книга при отказе без рецепта");
            cleanup(helper, spirit);
        });
    }

    private static void field(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
    }

    private static SpiritEntity prepare(GameTestHelper helper, ItemStack... stacks) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);
        for (ItemStack stack : stacks) {
            int pieces = stack.getMaxStackSize() == 1 ? stack.getCount() : 1;
            for (int piece = 0; piece < pieces; piece++) {
                spirit.getInventory().addItem(pieces == 1 ? stack.copy() : stack.copyWithCount(1));
            }
        }
        return spirit;
    }

    private static ItemStack stack(Item item, int count) {
        return new ItemStack(item, count);
    }

    private static SpiritRun launch(GameTestHelper helper, SpiritEntity spirit, String item, int count) {
        ScriptCall call = new ScriptCall("craft", Map.of("item", item, "count", count, "radius", RADIUS));
        return SpiritRun.launch(helper, spirit, call, SETTLE_TICKS);
    }

    private static void assertFinished(GameTestHelper helper, SpiritEntity spirit, SpiritRun run, RunStatus expected) {
        run.assertStarted();
        RunStatus actual = spirit.taskStatus().orElse(null);
        helper.assertTrue(actual == expected, "задача craft обязана завершиться статусом " + expected + ", статус: "
                + actual + ", причина: " + spirit.taskFailureReason().orElse("нет"));
    }

    private static void assertReason(GameTestHelper helper, SpiritEntity spirit, String fragment) {
        String reason = spirit.taskFailureReason().orElse("");
        helper.assertTrue(reason.contains(fragment),
                "причина провала обязана содержать '" + fragment + "', фактическая: '" + reason + "'");
    }

    private static void assertCount(GameTestHelper helper, SpiritEntity spirit, Item item, int expected, String what) {
        int actual = GameTestFixtures.countInInventory(spirit.getInventory(), item);
        helper.assertTrue(actual == expected, what + ": ожидалось " + expected + ", в инвентаре " + actual);
    }

    private static int tablesOnField(GameTestHelper helper) {
        int tables = 0;
        for (int x = 0; x < GameTestFixtures.FLOOR_SIZE; x++) {
            for (int z = 0; z < GameTestFixtures.FLOOR_SIZE; z++) {
                if (helper.getBlockState(new BlockPos(x, 1, z)).is(Blocks.CRAFTING_TABLE)) {
                    tables++;
                }
            }
        }
        return tables;
    }

    private static void cleanup(GameTestHelper helper, SpiritEntity spirit) {
        spirit.discard();
        GameTestFixtures.reset(helper);
    }
}
