package com.theminespirit.gametest;

import static com.theminespirit.gametest.FarmCropSupport.countBlock;
import static com.theminespirit.gametest.FarmCropSupport.cropAge;
import static com.theminespirit.gametest.FarmCropSupport.setCrop;
import static com.theminespirit.gametest.FarmTestSupport.FALL_SETTLE_TICKS;
import static com.theminespirit.gametest.FarmTestSupport.FLOOR_Y;
import static com.theminespirit.gametest.FarmTestSupport.SETTLE_TICKS;
import static com.theminespirit.gametest.FarmTestSupport.START;
import static com.theminespirit.gametest.FarmTestSupport.WATER;
import static com.theminespirit.gametest.FarmTestSupport.assertSucceeded;
import static com.theminespirit.gametest.FarmTestSupport.bed;
import static com.theminespirit.gametest.FarmTestSupport.boneMealCall;
import static com.theminespirit.gametest.FarmTestSupport.harvestCall;
import static com.theminespirit.gametest.FarmTestSupport.plantCall;
import static com.theminespirit.gametest.FarmTestSupport.spawnWith;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.farming.CropKind;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
final class FarmSafetyGameTests {
    private FarmSafetyGameTests() {}

    private static void arenaFloor(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
    }

    // [флак] замер 974 тика: посадка, подкормка и три сбора 9 клеток; потолок 1950 ≈ 2× замера
    @SceneStep(template = "arena", timeoutTicks = 1950)
    public static void farmCycle_PlantGrowHarvestThreeTimes_KeepsFarmlandAndSeeds(GameTestHelper helper) {
        arenaFloor(helper);
        helper.setBlock(WATER, Blocks.WATER);
        List<BlockPos> cells = bed();
        for (BlockPos cell : cells) {
            helper.setBlock(cell, Blocks.FARMLAND);
        }
        SpiritEntity spirit = spawnWith(
                helper,
                START,
                new ItemStack(Items.WHEAT_SEEDS, 17),
                new ItemStack(Items.BONE_MEAL, 64),
                new ItemStack(Items.BONE_MEAL, 64));
        List<TaskNode> steps = new ArrayList<>();
        steps.add(plantCall(CropKind.WHEAT, 64));
        for (int round = 0; round < 3; round++) {
            steps.add(boneMealCall(CropKind.WHEAT, 256));
            steps.add(harvestCall(CropKind.WHEAT, true));
        }
        SpiritRun.launch(helper, spirit, new SequenceNode(steps), SETTLE_TICKS);
        int seedsAfterPlanting = 17 - 9;

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "цикл посадка, подкормка и три сбора обязан завершиться успехом: статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            int seeds = GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHEAT_SEEDS);
            helper.assertTrue(
                    seeds >= seedsAfterPlanting,
                    "после трёх кругов семян обязано быть не меньше, чем после посадки (" + seedsAfterPlanting
                            + "): самоподдерживающаяся грядка не теряет семян, а осталось " + seeds);
            helper.assertTrue(
                    countBlock(helper, Blocks.FARMLAND) == 9,
                    "пашни обязано остаться 9, фактически " + countBlock(helper, Blocks.FARMLAND));
            for (BlockPos cell : cells) {
                helper.assertTrue(
                        helper.getBlockState(cell.above()).is(Blocks.WHEAT),
                        "после цикла пустых клеток быть не должно, а над " + cell + " "
                                + helper.getBlockState(cell.above()).getBlock());
            }
            int wheat = GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHEAT);
            helper.assertTrue(
                    wheat == 27, "за три сбора по 9 клеток обязано быть собрано 27 пшеницы, фактически " + wheat);
            GameTestFixtures.finish(helper);
        });
    }

    // [флак] замер 278 тиков: падение с высоты и сбор 9 клеток с пересадкой; потолок 420 ≈ 1.5× замера
    @SceneStep(template = "arena", timeoutTicks = 420)
    public static void harvestCrop_SpiritFallsOntoBed_DoesNotTrampleFarmland(GameTestHelper helper) {
        arenaFloor(helper);
        helper.setBlock(WATER, Blocks.WATER);
        List<BlockPos> cells = bed();
        for (BlockPos cell : cells) {
            setCrop(helper, cell, CropKind.WHEAT, 7);
        }
        SpiritEntity spirit = spawnWith(helper, new BlockPos(4, 4, 4), new ItemStack(Items.WHEAT_SEEDS, 3));
        SpiritRun run = SpiritRun.launch(helper, spirit, harvestCall(CropKind.WHEAT, true), FALL_SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            assertSucceeded(helper, spirit, "девять зрелых клеток обязаны собраться");
            helper.assertTrue(
                    countBlock(helper, Blocks.FARMLAND) == 9,
                    "дух упал на грядку с высоты и ходил по ней: пашни обязано остаться 9, осталось "
                            + countBlock(helper, Blocks.FARMLAND) + " (растоптанная пашня превращается в землю)");
            for (BlockPos cell : cells) {
                helper.assertTrue(cropAge(helper, cell) == 0, "каждая клетка обязана быть собрана и пересажена");
            }
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void farmland_SpiritDropsOntoFarmlandFiveTimes_StaysFarmland(GameTestHelper helper) {
        arenaFloor(helper);
        helper.setBlock(WATER, Blocks.WATER);
        List<BlockPos> cells = new ArrayList<>();
        for (int x = 3; x <= 7; x++) {
            BlockPos cell = new BlockPos(x, FLOOR_Y, 4);
            helper.setBlock(cell, Blocks.FARMLAND);
            cells.add(cell);
        }
        SpiritEntity spirit = spawnWith(helper, START, new ItemStack(Items.IRON_HOE));
        AtomicInteger drop = new AtomicInteger(0);
        AtomicBoolean lifted = new AtomicBoolean(false);

        helper.onEachTick(() -> {
            int index = drop.get();
            if (index >= cells.size()) {
                return;
            }
            BlockPos cell = cells.get(index);
            double cellY = helper.absolutePos(cell).getY();
            if (!lifted.get()) {
                spirit.teleportTo(
                        helper.absolutePos(cell).getX() + 0.5,
                        cellY + 2.0,
                        helper.absolutePos(cell).getZ() + 0.5);
                spirit.resetFallDistance();
                lifted.set(true);
                return;
            }
            if (spirit.onGround() && spirit.getY() <= cellY + 1.1) {
                drop.set(index + 1);
                lifted.set(false);
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    drop.get() >= cells.size(),
                    "подготовка: дух обязан упасть на каждую из " + cells.size() + " клеток пашни, упал на "
                            + drop.get());
            int farmland = countBlock(helper, Blocks.FARMLAND);
            helper.assertTrue(
                    farmland == cells.size(),
                    "дух упал с высоты на пашню " + cells.size() + " раз: пашня обязана остаться пашней, иначе "
                            + "растоптанная клетка превращается в землю и ферма в игре теряет грядку. Пашни "
                            + farmland + " из " + cells.size());
            for (BlockPos cell : cells) {
                helper.assertTrue(
                        helper.getBlockState(cell).is(Blocks.FARMLAND),
                        "клетка " + cell + " обязана остаться пашней, а там "
                                + helper.getBlockState(cell).getBlock());
            }
            GameTestFixtures.finish(helper);
        });
    }
}
