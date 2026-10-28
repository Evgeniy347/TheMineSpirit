package com.theminespirit.gametest;

import static com.theminespirit.gametest.ChopSceneSupport.buildDarkOakTree;
import static com.theminespirit.gametest.ChopSceneSupport.buildOakTree;
import static com.theminespirit.gametest.ChopSceneSupport.clearField;
import static com.theminespirit.gametest.ChopSceneSupport.prepareField;
import static com.theminespirit.gametest.ChopTestSupport.FIELD;
import static com.theminespirit.gametest.ChopTestSupport.PLATFORM;
import static com.theminespirit.gametest.ChopTestSupport.SETTLE_TICKS;
import static com.theminespirit.gametest.ChopTestSupport.TOOL_SLOT;
import static com.theminespirit.gametest.ChopTestSupport.chopCall;
import static com.theminespirit.gametest.ChopTestSupport.chopParams;
import static com.theminespirit.gametest.ChopTestSupport.countBlocksInWorld;
import static com.theminespirit.gametest.ChopTestSupport.driveScript;
import static com.theminespirit.gametest.ChopTestSupport.spawnChopper;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.neoforge.ChopTreeScript;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
final class ChopReplantGameTests {
    private ChopReplantGameTests() {}

    @SceneStep(template = FIELD, timeoutTicks = 400)
    public static void chopTree_ReplantWithOneSapling_PlantsItAtTrunkBase(GameTestHelper helper) {
        prepareField(helper);
        buildOakTree(helper, 4, 4, 5);
        SpiritEntity spirit = spawnChopper(helper);
        spirit.getInventory().setItem(1, new ItemStack(Items.OAK_SAPLING));

        SpiritRun run = SpiritRun.launch(helper, spirit, chopCall("oak", 1, true), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "рубка с replant и одним саженцем успешна, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(4, 1, 4)).is(Blocks.OAK_SAPLING),
                    "на месте ствола обязан стоять саженец дуба, а стоит "
                            + helper.getBlockState(new BlockPos(4, 1, 4)).getBlock());
            int saplings = GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_SAPLING);
            helper.assertTrue(saplings == 0, "посаженный саженец списывается из инвентаря, осталось " + saplings);
            int logs = GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_LOG);
            helper.assertTrue(logs == 5, "5 брёвен в инвентаре, фактически " + logs);

            clearField(helper);
        });
    }

    @SceneStep(template = FIELD, timeoutTicks = 400)
    public static void chopTree_ReplantWithoutSaplings_SucceedsAndReportsNoSaplings(GameTestHelper helper) {
        prepareField(helper);
        buildOakTree(helper, 4, 4, 5);
        SpiritEntity spirit = spawnChopper(helper);
        ChopTreeScript script = new ChopTreeScript(spirit, chopParams("oak", 1, true));
        ScriptStatus[] status = driveScript(helper, spirit, script);

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    status[0] == ScriptStatus.SUCCEEDED,
                    "нехватка саженцев не ошибка: рубка успешна, статус " + status[0] + ", причина: "
                            + script.failureReason());
            helper.assertTrue(
                    script.summary().contains("replant: нет саженцев"),
                    "итоговое сообщение обязано сказать «replant: нет саженцев», а оно: " + script.summary());
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(4, 1, 4)).isAir(),
                    "без саженцев на месте ствола ничего не появляется");

            clearField(helper);
        });
    }

    @SceneStep(template = FIELD, timeoutTicks = 400)
    public static void chopTree_ReplantDarkOakWithFourSaplings_PlantsAllFourCells(GameTestHelper helper) {
        prepareField(helper);
        buildDarkOakTree(helper, 4, 4, 4);
        SpiritEntity spirit = spawnChopper(helper);
        spirit.getInventory().setItem(1, new ItemStack(Items.DARK_OAK_SAPLING, 4));

        SpiritRun run = SpiritRun.launch(helper, spirit, chopCall("dark_oak", 1, true), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "тёмный дуб 2x2 срублен с посадкой, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            for (BlockPos cell : List.of(
                    new BlockPos(4, 1, 4), new BlockPos(5, 1, 4), new BlockPos(4, 1, 5), new BlockPos(5, 1, 5))) {
                helper.assertTrue(
                        helper.getBlockState(cell).is(Blocks.DARK_OAK_SAPLING),
                        "в клетке ствола " + cell + " обязан стоять саженец тёмного дуба, а стоит "
                                + helper.getBlockState(cell).getBlock());
            }
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.DARK_OAK_SAPLING) == 0,
                    "четыре саженца потрачены на четыре клетки");

            clearField(helper);
        });
    }

    @SceneStep(template = FIELD, timeoutTicks = 400)
    public static void chopTree_ReplantDarkOakWithThreeSaplings_PlantsOneByOneFallback(GameTestHelper helper) {
        prepareField(helper);
        buildDarkOakTree(helper, 4, 4, 4);
        SpiritEntity spirit = spawnChopper(helper);
        spirit.getInventory().setItem(1, new ItemStack(Items.DARK_OAK_SAPLING, 3));
        ChopTreeScript script = new ChopTreeScript(spirit, chopParams("dark_oak", 1, true));
        ScriptStatus[] status = driveScript(helper, spirit, script);

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    status[0] == ScriptStatus.SUCCEEDED,
                    "рубка успешна и при нехватке саженцев на квадрат, статус " + status[0] + ", причина: "
                            + script.failureReason());
            helper.assertTrue(
                    countBlocksInWorld(helper, Blocks.DARK_OAK_LOG) == 0,
                    "ствол 2×2 обязан срубиться целиком, иначе нехватка саженцев на четыре клетки "
                            + "не считается нехваткой: осталось брёвен "
                            + countBlocksInWorld(helper, Blocks.DARK_OAK_LOG));
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(4, 1, 4)).is(Blocks.DARK_OAK_SAPLING),
                    "на четыре клетки квадрата не хватает трёх саженцев: по одному сажается один саженец "
                            + "у основания, а в клетке " + new BlockPos(4, 1, 4) + " стоит "
                            + helper.getBlockState(new BlockPos(4, 1, 4)).getBlock());
            int left = GameTestFixtures.countInInventory(spirit.getInventory(), Items.DARK_OAK_SAPLING);
            helper.assertTrue(
                    left == 2, "из трёх саженцев потрачен один, в инвентаре остаётся два, фактически " + left);
            helper.assertTrue(
                    script.summary().contains("replant: посажено 1"),
                    "итог обязан назвать один посаженный саженец, а он: " + script.summary());

            clearField(helper);
        });
    }

    @SceneStep(template = FIELD, timeoutTicks = 400)
    public static void chopTree_DarkOakTwoByTwoFromNeighbourCell_ChoppedWhole(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        prepareField(helper);
        buildDarkOakTree(helper, 4, 4, 4);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 9));
        spirit.getInventory().setItem(TOOL_SLOT, new ItemStack(Items.IRON_AXE));

        SpiritRun run = SpiritRun.launch(helper, spirit, chopCall("dark_oak", 1), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "тёмный дуб 2×2 обязан рубиться с соседней клетки, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    ChopTestSupport.countBlocksInWorld(helper, Blocks.DARK_OAK_LOG) == 0,
                    "все брёвна тёмного дуба обязаны быть срублены, осталось "
                            + ChopTestSupport.countBlocksInWorld(helper, Blocks.DARK_OAK_LOG));
            int logsInInventory = GameTestFixtures.countInInventory(spirit.getInventory(), Items.DARK_OAK_LOG);
            helper.assertTrue(
                    logsInInventory >= 16,
                    "в инвентарь обязаны попасть брёвна всего 2×2 ствола (≥16), фактически " + logsInInventory);
            clearField(helper);
        });
    }

    // [флак] обновление участка: шесть саженцев — ствол и досадка сеткой, каждую клетку дух обходит;
    // потолок с запасом на флаки полного прогона
    @SceneStep(template = FIELD, timeoutTicks = 800)
    public static void chopTree_ReplantOakWithExtraSaplings_RenewsAreaAwayFromWall(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        prepareField(helper);
        buildOakTree(helper, 4, 4, 5);
        for (int z = 0; z < PLATFORM; z++) {
            for (int y = 1; y <= 3; y++) {
                helper.setBlock(8, y, z, Blocks.COBBLESTONE);
            }
        }
        SpiritEntity spirit = spawnChopper(helper);
        spirit.getInventory().setItem(1, new ItemStack(Items.OAK_SAPLING, 6));
        ChopTreeScript script = new ChopTreeScript(spirit, chopParams("oak", 1, true));
        ScriptStatus[] status = driveScript(helper, spirit, script);

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    status[0] == ScriptStatus.SUCCEEDED,
                    "рубка с обновлением участка обязана завершиться успехом, статус " + status[0] + ", причина: "
                            + script.failureReason() + ", итог: " + script.summary());
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(4, 1, 4)).is(Blocks.OAK_SAPLING),
                    "на месте ствола обязан стоять саженец дуба, а стоит "
                            + helper.getBlockState(new BlockPos(4, 1, 4)).getBlock());
            int saplings = countBlocksInWorld(helper, Blocks.OAK_SAPLING);
            helper.assertTrue(
                    saplings >= 2,
                    "шесть саженцев при одном на ствол дают досадку участка: всего посажено " + saplings);
            for (int x = 0; x < PLATFORM; x++) {
                for (int z = 0; z < PLATFORM; z++) {
                    if (!helper.getBlockState(new BlockPos(x, 1, z)).is(Blocks.OAK_SAPLING)) {
                        continue;
                    }
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            for (int y = 1; y <= 2; y++) {
                                helper.assertTrue(
                                        !helper.getBlockState(new BlockPos(x + dx, y, z + dz))
                                                .is(Blocks.COBBLESTONE),
                                        "саженец в клетке " + new BlockPos(x, 1, z) + " стоит вплотную к стене "
                                                + "игрока: зазор buildingGap=2 не соблюдён");
                            }
                        }
                    }
                }
            }
            clearField(helper);
        });
    }
}
