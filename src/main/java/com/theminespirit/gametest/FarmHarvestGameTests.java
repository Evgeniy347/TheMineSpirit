package com.theminespirit.gametest;

import static com.theminespirit.gametest.FarmCropSupport.countBlock;
import static com.theminespirit.gametest.FarmCropSupport.cropAge;
import static com.theminespirit.gametest.FarmCropSupport.harvestMixed;
import static com.theminespirit.gametest.FarmCropSupport.setCrop;
import static com.theminespirit.gametest.FarmTestSupport.FLOOR_Y;
import static com.theminespirit.gametest.FarmTestSupport.SETTLE_TICKS;
import static com.theminespirit.gametest.FarmTestSupport.START;
import static com.theminespirit.gametest.FarmTestSupport.WATER;
import static com.theminespirit.gametest.FarmTestSupport.assertFailedWith;
import static com.theminespirit.gametest.FarmTestSupport.assertSucceeded;
import static com.theminespirit.gametest.FarmTestSupport.bed;
import static com.theminespirit.gametest.FarmTestSupport.boneMealCall;
import static com.theminespirit.gametest.FarmTestSupport.changedBlocks;
import static com.theminespirit.gametest.FarmTestSupport.harvestCall;
import static com.theminespirit.gametest.FarmTestSupport.launch;
import static com.theminespirit.gametest.FarmTestSupport.snapshot;
import static com.theminespirit.gametest.FarmTestSupport.spawnWith;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.farming.CropKind;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
final class FarmHarvestGameTests {
    private FarmHarvestGameTests() {}

    private static void arenaFloor(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
    }

    @SceneStep(template = "arena", timeoutTicks = 260)
    public static void harvestCrop_MixedWheat_HarvestsAndReplantsOnlyMature(GameTestHelper helper) {
        harvestMixed(helper, CropKind.WHEAT);
    }

    @SceneStep(template = "arena", timeoutTicks = 260)
    public static void harvestCrop_MixedCarrots_HarvestsAndReplantsOnlyMature(GameTestHelper helper) {
        harvestMixed(helper, CropKind.CARROT);
    }

    @SceneStep(template = "arena", timeoutTicks = 270)
    public static void harvestCrop_MixedBeetroots_HarvestsAndReplantsOnlyMature(GameTestHelper helper) {
        harvestMixed(helper, CropKind.BEETROOT);
    }

    @SceneStep(template = "arena", timeoutTicks = 50)
    public static void harvestCrop_WheatFilter_LeavesMatureCarrotsAlone(GameTestHelper helper) {
        arenaFloor(helper);
        helper.setBlock(WATER, Blocks.WATER);
        BlockPos wheat = new BlockPos(3, FLOOR_Y, 3);
        BlockPos carrot = new BlockPos(5, FLOOR_Y, 5);
        setCrop(helper, wheat, CropKind.WHEAT, 7);
        setCrop(helper, carrot, CropKind.CARROT, 7);
        SpiritEntity spirit = spawnWith(helper, START, new ItemStack(Items.WHEAT_SEEDS, 3));
        SpiritRun run = launch(helper, spirit, harvestCall(CropKind.WHEAT, true));

        helper.succeedWhen(() -> {
            run.assertStarted();
            assertSucceeded(helper, spirit, "зрелая пшеница обязана собраться");
            helper.assertTrue(cropAge(helper, wheat) == 0, "собранная пшеница обязана быть пересажена");
            helper.assertTrue(
                    cropAge(helper, carrot) == 7
                            && helper.getBlockState(carrot.above()).is(Blocks.CARROTS),
                    "морковь при crop = wheat обязана остаться нетронутой и зрелой");
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.CARROT) == 0,
                    "морковь не должна попасть в инвентарь");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void harvestCrop_ReplantFalse_LeavesCellsEmptyAndFarmlandIntact(GameTestHelper helper) {
        arenaFloor(helper);
        helper.setBlock(WATER, Blocks.WATER);
        List<BlockPos> cells = bed();
        for (BlockPos cell : cells) {
            setCrop(helper, cell, CropKind.WHEAT, 7);
        }
        SpiritEntity spirit = spawnWith(helper, START, new ItemStack(Items.WHEAT_SEEDS, 3));
        SpiritRun run = launch(helper, spirit, harvestCall(CropKind.WHEAT, false));

        helper.succeedWhen(() -> {
            run.assertStarted();
            assertSucceeded(helper, spirit, "девять зрелых клеток обязаны собраться");
            for (BlockPos cell : cells) {
                helper.assertTrue(
                        helper.getBlockState(cell.above()).isAir(),
                        "при replant = false клетка " + cell + " обязана остаться пустой, а там "
                                + helper.getBlockState(cell.above()).getBlock());
            }
            helper.assertTrue(
                    countBlock(helper, Blocks.FARMLAND) == 9,
                    "пашни обязано остаться 9, фактически " + countBlock(helper, Blocks.FARMLAND));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHEAT_SEEDS) >= 3,
                    "без пересадки семена только прибавляются: было 3, стало "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHEAT_SEEDS));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void boneMeal_ImmatureAndMatureWheat_GrowsOnlyImmature(GameTestHelper helper) {
        arenaFloor(helper);
        helper.setBlock(WATER, Blocks.WATER);
        BlockPos young = new BlockPos(3, FLOOR_Y, 3);
        BlockPos mature = new BlockPos(3, FLOOR_Y, 5);
        setCrop(helper, young, CropKind.WHEAT, 0);
        setCrop(helper, mature, CropKind.WHEAT, 7);
        SpiritEntity spirit = spawnWith(helper, START, new ItemStack(Items.BONE_MEAL, 16));
        SpiritRun run = launch(helper, spirit, boneMealCall(CropKind.WHEAT, 16));

        helper.succeedWhen(() -> {
            run.assertStarted();
            assertSucceeded(helper, spirit, "недозрелая пшеница обязана дорасти, зрелая муку не просит");
            helper.assertTrue(
                    cropAge(helper, young) == 7,
                    "костная мука обязана довести недозрелую пшеницу до зрелости, возраст " + cropAge(helper, young));
            int spent = 16 - GameTestFixtures.countInInventory(spirit.getInventory(), Items.BONE_MEAL);
            helper.assertTrue(
                    spent >= 2 && spent <= 4,
                    "возраст растёт на 2..5 за единицу, поэтому до зрелости хватит 2..4 единиц, потрачено " + spent);
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void boneMeal_CountOne_SpendsExactlyOneUnit(GameTestHelper helper) {
        arenaFloor(helper);
        helper.setBlock(WATER, Blocks.WATER);
        setCrop(helper, new BlockPos(3, FLOOR_Y, 3), CropKind.WHEAT, 0);
        setCrop(helper, new BlockPos(3, FLOOR_Y, 5), CropKind.WHEAT, 0);
        SpiritEntity spirit = spawnWith(helper, START, new ItemStack(Items.BONE_MEAL, 5));
        SpiritRun run = launch(helper, spirit, boneMealCall(CropKind.WHEAT, 1));

        helper.succeedWhen(() -> {
            run.assertStarted();
            assertSucceeded(helper, spirit, "одна единица муки обязана быть потрачена, задача успешна");
            int left = GameTestFixtures.countInInventory(spirit.getInventory(), Items.BONE_MEAL);
            helper.assertTrue(
                    left == 4, "при count = 1 тратится ровно одна единица: из 5 остаться 4, осталось " + left);
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 100)
    public static void boneMeal_WithoutMeal_FailsFastAndLeavesWorldUnchanged(GameTestHelper helper) {
        arenaFloor(helper);
        helper.setBlock(WATER, Blocks.WATER);
        setCrop(helper, new BlockPos(3, FLOOR_Y, 3), CropKind.WHEAT, 0);
        SpiritEntity spirit = spawnWith(helper, START, new ItemStack(Items.WHEAT_SEEDS, 3));
        Map<BlockPos, Block> before = snapshot(helper);
        SpiritRun run = launch(helper, spirit, boneMealCall(CropKind.WHEAT, 4));

        helper.succeedWhen(() -> {
            run.assertStarted();
            assertFailedWith(helper, spirit, "костной муки");
            helper.assertTrue(
                    run.spiritTicks() <= 2,
                    "без муки отказ обязан случиться до первого действия, за 2 тика духа, заняло " + run.spiritTicks());
            helper.assertTrue(
                    changedBlocks(helper, before) == 0 && cropAge(helper, new BlockPos(3, FLOOR_Y, 3)) == 0,
                    "при отказе мир не меняется");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 200)
    public static void boneMeal_OnlyMatureCrops_FailsAndSpendsNothing(GameTestHelper helper) {
        arenaFloor(helper);
        helper.setBlock(WATER, Blocks.WATER);
        for (BlockPos cell : bed()) {
            setCrop(helper, cell, CropKind.WHEAT, 7);
        }
        SpiritEntity spirit = spawnWith(helper, START, new ItemStack(Items.BONE_MEAL, 5));
        SpiritRun run = launch(helper, spirit, boneMealCall(CropKind.WHEAT, 4));

        helper.succeedWhen(() -> {
            run.assertStarted();
            assertFailedWith(helper, spirit, "недозрелых посевов нет");
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.BONE_MEAL) == 5,
                    "на зрелые посевы мука не тратится: обязано остаться 5, осталось "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.BONE_MEAL));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 300)
    public static void harvestCrop_SpiritWalledInWithinReach_BreaksCropFromWhereItStands(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        BlockPos cell = new BlockPos(4, FLOOR_Y, 4);
        setCrop(helper, cell, CropKind.WHEAT, 7);
        BlockPos standing = new BlockPos(2, 1, 4);
        helper.setBlock(standing.below(), Blocks.FARMLAND);
        helper.setBlock(new BlockPos(1, FLOOR_Y, 4), Blocks.WATER);
        for (int y = 1; y <= 2; y++) {
            helper.setBlock(standing.west().atY(y), Blocks.STONE);
            helper.setBlock(standing.east().atY(y), Blocks.STONE);
            helper.setBlock(standing.north().atY(y), Blocks.STONE);
            helper.setBlock(standing.south().atY(y), Blocks.STONE);
        }
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), standing);
        ScriptCall call = new ScriptCall("harvest_crop", Map.of("crop", "wheat", "radius", 8, "replant", false));
        SpiritRun run = SpiritRun.launch(helper, spirit, call, SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    helper.getBlockState(cell.above()).isAir(),
                    "дух заперт на пашне, предпочтённая стоянка недостижима, но зрелая клетка в досягаемости: когда "
                            + "навигация не доводит духа до стоянки, работа обязана идти оттуда, где он стоит, а не "
                            + "считаться «без места для духа». Статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            GameTestFixtures.finish(helper);
        });
    }
}
