package com.theminespirit.gametest;

import static com.theminespirit.gametest.FarmCropSupport.countBlock;
import static com.theminespirit.gametest.FarmTestSupport.FLOOR_Y;
import static com.theminespirit.gametest.FarmTestSupport.SETTLE_TICKS;
import static com.theminespirit.gametest.FarmTestSupport.START;
import static com.theminespirit.gametest.FarmTestSupport.TILL_SPIRIT;
import static com.theminespirit.gametest.FarmTestSupport.WATER;
import static com.theminespirit.gametest.FarmTestSupport.assertFailedWith;
import static com.theminespirit.gametest.FarmTestSupport.assertSucceeded;
import static com.theminespirit.gametest.FarmTestSupport.changedBlocks;
import static com.theminespirit.gametest.FarmTestSupport.fillGround;
import static com.theminespirit.gametest.FarmTestSupport.launch;
import static com.theminespirit.gametest.FarmTestSupport.snapshot;
import static com.theminespirit.gametest.FarmTestSupport.spawnWith;
import static com.theminespirit.gametest.FarmTestSupport.tillCall;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.neoforge.SpiritEntity;
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
final class FarmTillingGameTests {
    private FarmTillingGameTests() {}

    private static void arenaFloor(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
    }

    @SceneStep(template = "arena", timeoutTicks = 120)
    public static void till_FiveByFiveWithWater_TillsTwelveWithinFourOfWaterAndWearsHoe(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        for (int x = 3; x <= 7; x++) {
            for (int z = 3; z <= 7; z++) {
                helper.setBlock(new BlockPos(x, FLOOR_Y, z), Blocks.DIRT);
            }
        }
        BlockPos water = new BlockPos(5, FLOOR_Y, 5);
        helper.setBlock(water, Blocks.WATER);
        SpiritEntity spirit = spawnWith(helper, new BlockPos(5, 1, 1), new ItemStack(Items.IRON_HOE));
        Map<BlockPos, Block> before = snapshot(helper);
        SpiritRun run = SpiritRun.launch(helper, spirit, tillCall(4, 12), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "двенадцать клеток земли у воды обязаны вспахаться: статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            int farmland = 0;
            for (Map.Entry<BlockPos, Block> entry : before.entrySet()) {
                if (helper.getBlockState(entry.getKey()).is(Blocks.FARMLAND)) {
                    farmland++;
                    int dx = Math.abs(entry.getKey().getX() - water.getX());
                    int dz = Math.abs(entry.getKey().getZ() - water.getZ());
                    helper.assertTrue(
                            dx <= 4 && dz <= 4, "пашня " + entry.getKey() + " дальше 4 блоков от воды: она пересохнет");
                }
            }
            helper.assertTrue(farmland == 12, "обязано появиться ровно 12 пашни, фактически " + farmland);
            helper.assertTrue(
                    spirit.getInventory().getItem(0).getDamageValue() == 12,
                    "износ мотыги обязан быть 12 (по единице на клетку, как у игрока), фактически "
                            + spirit.getInventory().getItem(0).getDamageValue());
            helper.assertTrue(
                    changedBlocks(helper, before) == 12,
                    "кроме 12 вспаханных клеток мир обязан остаться прежним, изменилось "
                            + changedBlocks(helper, before));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void till_DirtFiveBlocksFromWater_IsLeftUntilled(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(new BlockPos(1, FLOOR_Y, 5), Blocks.WATER);
        helper.setBlock(new BlockPos(5, FLOOR_Y, 5), Blocks.DIRT);
        helper.setBlock(new BlockPos(6, FLOOR_Y, 5), Blocks.DIRT);
        SpiritEntity spirit = spawnWith(helper, new BlockPos(5, 1, 2), new ItemStack(Items.IRON_HOE));
        SpiritRun run = SpiritRun.launch(helper, spirit, tillCall(4, 2), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "единственная годная клетка вспахивается, задача идёт до конца: статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(5, FLOOR_Y, 5)).is(Blocks.FARMLAND),
                    "земля в 4 блоках от воды обязана стать пашней");
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(6, FLOOR_Y, 5)).is(Blocks.DIRT),
                    "земля в 5 блоках от воды обязана остаться землёй: вспаханная там пашня пересохла бы");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 100)
    public static void till_WithoutHoe_FailsWithinTwoTicksAndLeavesWorldUnchanged(GameTestHelper helper) {
        arenaFloor(helper);
        fillGround(helper, Blocks.DIRT);
        helper.setBlock(WATER, Blocks.WATER);
        SpiritEntity spirit = spawnWith(helper, START, new ItemStack(Items.WHEAT_SEEDS, 4));
        Map<BlockPos, Block> before = snapshot(helper);
        SpiritRun run = launch(helper, spirit, tillCall(6, 5));

        helper.succeedWhen(() -> {
            run.assertStarted();
            assertFailedWith(helper, spirit, "мотыги");
            helper.assertTrue(
                    run.spiritTicks() <= 2,
                    "без мотыги отказ обязан случиться до первого действия, за 2 тика духа, заняло "
                            + run.spiritTicks());
            helper.assertTrue(
                    changedBlocks(helper, before) == 0,
                    "при отказе мир не меняется, изменилось блоков: " + changedBlocks(helper, before));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void till_HoeOnLastUse_FailsBeforeTillingWithoutWear(GameTestHelper helper) {
        arenaFloor(helper);
        helper.setBlock(WATER, Blocks.WATER);
        helper.setBlock(new BlockPos(3, FLOOR_Y, 3), Blocks.DIRT);
        helper.setBlock(new BlockPos(4, FLOOR_Y, 3), Blocks.DIRT);
        helper.setBlock(new BlockPos(5, FLOOR_Y, 3), Blocks.DIRT);
        ItemStack hoe = new ItemStack(Items.IRON_HOE);
        int lastUseDamage = hoe.getMaxDamage() - 1;
        hoe.setDamageValue(lastUseDamage);
        SpiritEntity spirit = spawnWith(helper, START, hoe);
        SpiritRun run = launch(helper, spirit, tillCall(6, 5));

        helper.succeedWhen(() -> {
            run.assertStarted();
            assertFailedWith(helper, spirit, "мотыги");
            helper.assertTrue(
                    run.spiritTicks() <= 2,
                    "изношенная мотыга не тратит последний удар: отказ обязан случиться до первого действия, за 2 тика "
                            + "духа, заняло " + run.spiritTicks());
            helper.assertTrue(
                    countBlock(helper, Blocks.FARMLAND) == 0,
                    "ни одна клетка не вспахана изношенной мотыгой, фактически пашни "
                            + countBlock(helper, Blocks.FARMLAND));
            helper.assertTrue(
                    spirit.getInventory().getItem(0).getDamageValue() == lastUseDamage,
                    "мотыга не должна изнашиваться дальше, фактический износ "
                            + spirit.getInventory().getItem(0).getDamageValue());
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void till_WeedAboveDirt_CutsWeedAndTills(GameTestHelper helper) {
        arenaFloor(helper);
        helper.setBlock(WATER, Blocks.WATER);
        helper.setBlock(new BlockPos(4, FLOOR_Y, 4), Blocks.DIRT);
        helper.setBlock(new BlockPos(4, FLOOR_Y + 1, 4), Blocks.SHORT_GRASS);
        helper.setBlock(new BlockPos(5, FLOOR_Y, 4), Blocks.DIRT);
        SpiritEntity spirit = spawnWith(helper, TILL_SPIRIT, new ItemStack(Items.IRON_HOE));
        SpiritRun run = launch(helper, spirit, tillCall(4, 2));

        helper.succeedWhen(() -> {
            run.assertStarted();
            assertSucceeded(helper, spirit, "обе клетки, и с травой, и без, обязаны вспахаться");
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(4, FLOOR_Y, 4)).is(Blocks.FARMLAND),
                    "земля под сорной травой обязана стать пашней");
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(4, FLOOR_Y + 1, 4)).isAir(),
                    "сорная трава обязана быть срублена рукой");
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(5, FLOOR_Y, 4)).is(Blocks.FARMLAND),
                    "чистая земля обязана стать пашней");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 200)
    public static void till_StoneAboveDirt_SkipsCellAndCountsIt(GameTestHelper helper) {
        arenaFloor(helper);
        helper.setBlock(WATER, Blocks.WATER);
        helper.setBlock(new BlockPos(4, FLOOR_Y, 4), Blocks.DIRT);
        helper.setBlock(new BlockPos(4, FLOOR_Y + 1, 4), Blocks.STONE);
        SpiritEntity spirit = spawnWith(helper, TILL_SPIRIT, new ItemStack(Items.IRON_HOE));
        SpiritRun run = launch(helper, spirit, tillCall(4, 1));

        helper.succeedWhen(() -> {
            run.assertStarted();
            assertFailedWith(helper, spirit, "не подходит: ");
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(4, FLOOR_Y, 4)).is(Blocks.DIRT),
                    "земля под камнем не вспахивается");
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(4, FLOOR_Y + 1, 4)).is(Blocks.STONE),
                    "камень над землёй дух не ломает");
            GameTestFixtures.finish(helper);
        });
    }
}
