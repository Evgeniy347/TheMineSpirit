package com.theminespirit.gametest;

import static com.theminespirit.gametest.FarmCropSupport.blockAge;
import static com.theminespirit.gametest.FarmCropSupport.columnHarvest;
import static com.theminespirit.gametest.FarmCropSupport.countBlock;
import static com.theminespirit.gametest.FarmCropSupport.cropAge;
import static com.theminespirit.gametest.FarmCropSupport.fruitHarvest;
import static com.theminespirit.gametest.FarmCropSupport.setColumn;
import static com.theminespirit.gametest.FarmCropSupport.setCrop;
import static com.theminespirit.gametest.FarmTestSupport.FLOOR_Y;
import static com.theminespirit.gametest.FarmTestSupport.START;
import static com.theminespirit.gametest.FarmTestSupport.WATER;
import static com.theminespirit.gametest.FarmTestSupport.assertSucceeded;
import static com.theminespirit.gametest.FarmTestSupport.bed;
import static com.theminespirit.gametest.FarmTestSupport.harvestCall;
import static com.theminespirit.gametest.FarmTestSupport.launch;
import static com.theminespirit.gametest.FarmTestSupport.spawnWith;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.farming.CropKind;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
final class FarmSpecialCropsGameTests {
    private FarmSpecialCropsGameTests() {}

    private static void arenaFloor(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
    }

    @SceneStep(template = "arena", timeoutTicks = 300)
    public static void harvestCrop_MixedSweetBerry_PicksWithoutBreakingAndKeepsBush(GameTestHelper helper) {
        arenaFloor(helper);
        helper.setBlock(WATER, Blocks.WATER);
        List<BlockPos> cells = bed();
        CropKind kind = CropKind.parse("sweet_berry_bush");
        for (int i = 0; i < cells.size(); i++) {
            setCrop(helper, cells.get(i), kind, i % 2 == 0 ? 3 : 1);
        }
        SpiritEntity spirit = spawnWith(helper, START);
        SpiritRun run = launch(helper, spirit, harvestCall(kind, true));

        helper.succeedWhen(() -> {
            run.assertStarted();
            assertSucceeded(
                    helper, spirit, "пять зрелых кустов обязаны собраться PICK без ломки, недозрелые не мешают");
            for (int i = 0; i < cells.size(); i++) {
                int expectedAge = 1;
                helper.assertTrue(
                        cropAge(helper, cells.get(i)) == expectedAge,
                        "клетка " + i
                                + " обязана иметь возраст 1 (зрелая собрана в 1, недозрелая 1 осталась), фактически "
                                + cropAge(helper, cells.get(i)));
                helper.assertTrue(
                        helper.getBlockState(cells.get(i).above()).is(Blocks.SWEET_BERRY_BUSH),
                        "куст на клетке " + i + " обязан остаться: PICK не ломает блок");
            }
            helper.assertTrue(
                    countBlock(helper, Blocks.FARMLAND) == 9,
                    "пашни обязано остаться 9, фактически " + countBlock(helper, Blocks.FARMLAND));
            int berries = GameTestFixtures.countInInventory(spirit.getInventory(), Items.SWEET_BERRIES);
            helper.assertTrue(
                    berries >= 8,
                    "с пяти зрелых кустов обязано быть не меньше 8 ягод (2-3 с куста), фактически " + berries);
            int bushes = countBlock(helper, Blocks.SWEET_BERRY_BUSH);
            helper.assertTrue(bushes == 9, "кустов обязано остаться 9, фактически " + bushes);
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void harvestCrop_SugarCaneColumn_LeavesBaseAndCollectsUpper(GameTestHelper helper) {
        columnHarvest(helper, CropKind.parse("sugar_cane"), Blocks.SUGAR_CANE, Blocks.SAND);
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void harvestCrop_BambooColumn_LeavesBaseAndCollectsUpper(GameTestHelper helper) {
        columnHarvest(helper, CropKind.parse("bamboo"), Blocks.BAMBOO, Blocks.DIRT);
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void harvestCrop_CactusColumn_LeavesBaseAndCollectsUpper(GameTestHelper helper) {
        columnHarvest(helper, CropKind.parse("cactus"), Blocks.CACTUS, Blocks.SAND);
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void harvestCrop_TallCactusColumn_KeepsAllDropsAboveBase(GameTestHelper helper) {
        arenaFloor(helper);
        BlockPos base = new BlockPos(4, FLOOR_Y, 4);
        helper.setBlock(base, Blocks.SAND);
        setColumn(helper, base, Blocks.CACTUS, 4);
        SpiritEntity spirit = spawnWith(helper, START);
        SpiritRun run = launch(helper, spirit, harvestCall(CropKind.parse("cactus"), false));

        helper.succeedWhen(() -> {
            run.assertStarted();
            assertSucceeded(helper, spirit, "кактус высотой 4 обязан собраться сверху вниз");
            helper.assertTrue(
                    helper.getBlockState(base.above()).is(Blocks.CACTUS),
                    "нижний кактус обязан остаться, а там "
                            + helper.getBlockState(base.above()).getBlock());
            for (int level = 2; level <= 4; level++) {
                helper.assertTrue(
                        helper.getBlockState(base.above(level)).isAir(),
                        "блок " + level + " столба обязан быть убран, а там "
                                + helper.getBlockState(base.above(level)).getBlock());
            }
            int count = GameTestFixtures.countInInventory(spirit.getInventory(), Items.CACTUS);
            helper.assertTrue(
                    count == 3,
                    "с кактуса высотой 4 (нижний оставлен) обязано собраться ровно 3 кактуса: дроп не должен "
                            + "сгорать на нижнем кактусе, фактически " + count);
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 300)
    public static void harvestCrop_SingleCane_IsIgnoredAndBaseStays(GameTestHelper helper) {
        arenaFloor(helper);
        BlockPos ground = new BlockPos(4, FLOOR_Y, 4);
        helper.setBlock(ground, Blocks.SAND);
        helper.setBlock(ground.above(), Blocks.SUGAR_CANE);
        SpiritEntity spirit = spawnWith(helper, START);
        SpiritRun run = launch(helper, spirit, harvestCall(CropKind.parse("sugar_cane"), false));

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "одиночный тростник высотой 1 не цель: задача обязана провалиться, а статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет"));
            helper.assertTrue(
                    helper.getBlockState(ground.above()).is(Blocks.SUGAR_CANE),
                    "одиночный тростник обязан остаться, а там "
                            + helper.getBlockState(ground.above()).getBlock());
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.SUGAR_CANE) == 0,
                    "урожая с одиночного столба быть не должно");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 300)
    public static void harvestCrop_MelonStemWithFruit_BreaksFruitKeepsStemAndFarmland(GameTestHelper helper) {
        fruitHarvest(
                helper,
                CropKind.MELON,
                Blocks.MELON_STEM,
                Blocks.MELON,
                Items.MELON_SLICE,
                3,
                "плод арбуза даёт 3–7 ломтиков");
    }

    @SceneStep(template = "arena", timeoutTicks = 300)
    public static void harvestCrop_PumpkinStemWithFruit_BreaksFruitKeepsStemAndFarmland(GameTestHelper helper) {
        fruitHarvest(
                helper,
                CropKind.PUMPKIN,
                Blocks.PUMPKIN_STEM,
                Blocks.PUMPKIN,
                Items.PUMPKIN,
                1,
                "плод тыквы даёт одну тыкву");
    }

    @SceneStep(template = "arena", timeoutTicks = 300)
    public static void harvestCrop_CocoaOnJungleLog_CollectsAndReplantsOnSameSide(GameTestHelper helper) {
        arenaFloor(helper);
        BlockPos log = new BlockPos(4, FLOOR_Y + 1, 4);
        helper.setBlock(log, Blocks.JUNGLE_LOG);
        BlockPos cocoa = new BlockPos(5, FLOOR_Y + 1, 4);
        helper.setBlock(
                cocoa,
                Blocks.COCOA
                        .defaultBlockState()
                        .setValue(CocoaBlock.AGE, CropKind.COCOA.maxAge())
                        .setValue(CocoaBlock.FACING, Direction.WEST));
        helper.setBlock(new BlockPos(4, FLOOR_Y + 1, 3), Blocks.STONE);
        helper.setBlock(new BlockPos(4, FLOOR_Y + 1, 5), Blocks.STONE);
        helper.setBlock(new BlockPos(3, FLOOR_Y + 1, 4), Blocks.STONE);
        SpiritEntity spirit = spawnWith(helper, START, new ItemStack(Items.COCOA_BEANS, 1));
        SpiritRun run = launch(helper, spirit, harvestCall(CropKind.COCOA, true));

        helper.succeedWhen(() -> {
            run.assertStarted();
            assertSucceeded(helper, spirit, "спелое какао обязано собраться и пересадиться на тот же бок");
            helper.assertTrue(
                    helper.getBlockState(log).is(Blocks.JUNGLE_LOG),
                    "ствол джунглей обязан остаться, а там "
                            + helper.getBlockState(log).getBlock());
            helper.assertTrue(
                    helper.getBlockState(cocoa).is(Blocks.COCOA),
                    "какао обязано быть пересажено на тот же бок ствола, а там "
                            + helper.getBlockState(cocoa).getBlock());
            helper.assertTrue(
                    blockAge(helper, cocoa) <= 1,
                    "пересаженный боб моложе спелого: возраст " + blockAge(helper, cocoa)
                            + ", а исходное спелое какао было возраста " + CropKind.COCOA.maxAge());
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.COCOA_BEANS) >= 1,
                    "собранные бобы минус один пересаженный обязаны остаться в инвентаре, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COCOA_BEANS));
            GameTestFixtures.finish(helper);
        });
    }
}
