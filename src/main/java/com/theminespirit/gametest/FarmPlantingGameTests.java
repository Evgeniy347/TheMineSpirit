package com.theminespirit.gametest;

import static com.theminespirit.gametest.FarmCropSupport.countBlock;
import static com.theminespirit.gametest.FarmCropSupport.cropAge;
import static com.theminespirit.gametest.FarmTestSupport.CROP_MIN_LIGHT;
import static com.theminespirit.gametest.FarmTestSupport.FLOOR_Y;
import static com.theminespirit.gametest.FarmTestSupport.SETTLE_TICKS;
import static com.theminespirit.gametest.FarmTestSupport.START;
import static com.theminespirit.gametest.FarmTestSupport.WATER;
import static com.theminespirit.gametest.FarmTestSupport.assertFailedWith;
import static com.theminespirit.gametest.FarmTestSupport.assertSucceeded;
import static com.theminespirit.gametest.FarmTestSupport.bed;
import static com.theminespirit.gametest.FarmTestSupport.brightnessAbove;
import static com.theminespirit.gametest.FarmTestSupport.changedBlocks;
import static com.theminespirit.gametest.FarmTestSupport.drive;
import static com.theminespirit.gametest.FarmTestSupport.finished;
import static com.theminespirit.gametest.FarmTestSupport.launch;
import static com.theminespirit.gametest.FarmTestSupport.plantCall;
import static com.theminespirit.gametest.FarmTestSupport.snapshot;
import static com.theminespirit.gametest.FarmTestSupport.spawnWith;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.farming.CropKind;
import com.theminespirit.neoforge.FarmSession;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
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
final class FarmPlantingGameTests {
    private FarmPlantingGameTests() {}

    private static void arenaFloor(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
    }

    @SceneStep(template = "arena", timeoutTicks = 120)
    public static void plantCrop_WheatOnNineFarmland_PlantsNineAndKeepsOneSeed(GameTestHelper helper) {
        arenaFloor(helper);
        helper.setBlock(WATER, Blocks.WATER);
        List<BlockPos> cells = bed();
        for (BlockPos cell : cells) {
            helper.setBlock(cell, Blocks.FARMLAND);
        }
        SpiritEntity spirit = spawnWith(helper, START, new ItemStack(Items.WHEAT_SEEDS, 10));
        SpiritRun run = launch(helper, spirit, plantCall(CropKind.WHEAT, 64));

        helper.succeedWhen(() -> {
            run.assertStarted();
            assertSucceeded(helper, spirit, "девять клеток пашни обязаны засадиться");
            for (BlockPos cell : cells) {
                helper.assertTrue(
                        helper.getBlockState(cell.above()).is(Blocks.WHEAT) && cropAge(helper, cell) == 0,
                        "над пашней " + cell + " обязана расти пшеница возраста 0, а там "
                                + helper.getBlockState(cell.above()).getBlock());
            }
            int seeds = GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHEAT_SEEDS);
            helper.assertTrue(seeds == 1, "из 10 семян должно остаться ровно 1, фактически " + seeds);
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 100)
    public static void plantCrop_WithoutSeeds_FailsFastAndLeavesWorldUnchanged(GameTestHelper helper) {
        arenaFloor(helper);
        helper.setBlock(WATER, Blocks.WATER);
        for (BlockPos cell : bed()) {
            helper.setBlock(cell, Blocks.FARMLAND);
        }
        SpiritEntity spirit = spawnWith(helper, START, new ItemStack(Items.IRON_HOE));
        Map<BlockPos, Block> before = snapshot(helper);
        SpiritRun run = launch(helper, spirit, plantCall(CropKind.WHEAT, 64));

        helper.succeedWhen(() -> {
            run.assertStarted();
            assertFailedWith(helper, spirit, "нет семян");
            helper.assertTrue(
                    run.spiritTicks() <= 2,
                    "без семян отказ обязан случиться до первого действия, за 2 тика духа, заняло "
                            + run.spiritTicks());
            helper.assertTrue(
                    changedBlocks(helper, before) == 0,
                    "при отказе мир не меняется, изменилось блоков: " + changedBlocks(helper, before));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void plantCrop_SeedsRunOutAfterTwoCells_FailsNamingPlantedProgress(GameTestHelper helper) {
        arenaFloor(helper);
        helper.setBlock(WATER, Blocks.WATER);
        for (BlockPos cell : bed()) {
            helper.setBlock(cell, Blocks.FARMLAND);
        }
        SpiritEntity spirit = spawnWith(helper, START, new ItemStack(Items.WHEAT_SEEDS, 2));
        AtomicReference<FarmSession> session =
                drive(helper, spirit, ctx -> FarmSession.plantCrop(ctx, CropKind.WHEAT, 4, 4));

        helper.succeedWhen(() -> {
            FarmSession run = finished(helper, session);
            helper.assertTrue(
                    run.outcome() == FarmSession.Outcome.FAILED,
                    "после двух посадок из двух семян сессия обязана завершиться провалом с понятным итогом: "
                            + run.outcome() + ", " + run.failureReason());
            String reason = run.failureReason() == null ? "" : run.failureReason();
            helper.assertTrue(
                    reason.contains("посажено 2"),
                    "причина провала обязана назвать две посаженные клетки, фактически: " + reason);
            helper.assertTrue(
                    reason.contains("нет семян"),
                    "причина провала обязана назвать кончившиеся семена, фактически: " + reason);
            helper.assertTrue(
                    countBlock(helper, Blocks.WHEAT) == 2,
                    "посажено ровно две клетки, фактически пшеницы " + countBlock(helper, Blocks.WHEAT));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void plantCrop_CellWithoutLight_IsNotPlantedAndCounted(GameTestHelper helper) {
        arenaFloor(helper);
        helper.setBlock(new BlockPos(4, FLOOR_Y, 6), Blocks.WATER);
        BlockPos lit = new BlockPos(2, FLOOR_Y, 6);
        BlockPos dark = new BlockPos(6, FLOOR_Y, 6);
        helper.setBlock(lit, Blocks.FARMLAND);
        helper.setBlock(dark, Blocks.FARMLAND);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    helper.setBlock(dark.offset(dx, 1, dz), Blocks.STONE);
                }
                helper.setBlock(dark.offset(dx, 2, dz), Blocks.STONE);
            }
        }
        SpiritEntity spirit = spawnWith(helper, START, new ItemStack(Items.WHEAT_SEEDS, 4));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                plantCall(CropKind.WHEAT, 64),
                SETTLE_TICKS,
                () -> brightnessAbove(helper, lit) >= CROP_MIN_LIGHT && brightnessAbove(helper, dark) < CROP_MIN_LIGHT);

        helper.succeedWhen(() -> {
            run.assertStarted();
            int brightness = brightnessAbove(helper, dark);
            helper.assertTrue(
                    brightness < CROP_MIN_LIGHT,
                    "подготовка: замурованная клетка обязана быть тёмной, а яркость " + brightness);
            assertFailedWith(helper, spirit, "не подходит: 1");
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(
                    reason.contains("посажено 1 из 2"),
                    "причина обязана назвать одну посаженную клетку из двух, фактически: " + reason);
            helper.assertTrue(
                    helper.getBlockState(lit.above()).is(Blocks.WHEAT), "освещённая клетка обязана быть засажена");
            helper.assertTrue(
                    helper.getBlockState(dark.above()).isAir(),
                    "в тёмной клетке посев не удержится: она обязана остаться пустой");
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHEAT_SEEDS) == 3,
                    "семя на тёмную клетку не списывается: из 4 должно остаться 3");
            GameTestFixtures.finish(helper);
        });
    }
}
