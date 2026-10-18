package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.TaskProgress;
import com.theminespirit.core.engine.TaskSnapshotCodec;
import com.theminespirit.core.farming.Above;
import com.theminespirit.core.farming.CropKind;
import com.theminespirit.core.farming.CropState;
import com.theminespirit.core.farming.FarmJob;
import com.theminespirit.core.farming.FarmPlanner;
import com.theminespirit.core.farming.FarmTile;
import com.theminespirit.core.farming.FarmTuning;
import com.theminespirit.core.farming.Ground;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.BlockBreaker;
import com.theminespirit.neoforge.CropRegistry;
import com.theminespirit.neoforge.FarmSession;
import com.theminespirit.neoforge.FarmTargets;
import com.theminespirit.neoforge.LevelFarmView;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FarmGameTests {
    private static final int GROUND_Y = 1;
    private static final int FLOOR_Y = 0;
    private static final int SETTLE_TICKS = 5;
    private static final int ARENA_SIZE = 11;
    private static final int ARENA_HEIGHT = 8;
    private static final int CROP_MIN_LIGHT = 8;
    private static final int FALL_SETTLE_TICKS = 30;
    private static final int VIEW_READS_PER_CELL_PER_CALL = 200;
    private static final int SAFETY_READS_PER_CELL_PER_CALL = 324;
    private static final BlockPos START = new BlockPos(1, 1, 1);
    private static final BlockPos TILL_SPIRIT = new BlockPos(5, 1, 7);
    private static final BlockPos WATER = new BlockPos(1, FLOOR_Y, 4);

    private FarmGameTests() {
    }

    // Сцены сладких ягод и столбовых культур проверяют только сбор и подбор. Раньше на каждую
    // такую пачку стояла пара @BeforeBatch/@AfterBatch с выключением координатора и доски;
    // теперь обе системы выключены на GameTest-сервере по умолчанию, и пачке ничего не нужно.
    private static final String BERRY_HARVEST_BATCH = "farm_harvest_sweetberry";
    private static final String BERRY_SESSION_BATCH = "farm_session_sweetberry";
    private static final String CANE_BATCH = "farm_column_sugarcane";
    private static final String BAMBOO_BATCH = "farm_column_bamboo";
    private static final String CACTUS_BATCH = "farm_column_cactus";
    private static final String SINGLE_COLUMN_BATCH = "farm_column_single";
    private static final String MELON_BATCH = "farm_fruit_melon";
    private static final String PUMPKIN_BATCH = "farm_fruit_pumpkin";
    private static final String COCOA_BATCH = "farm_cocoa";

    @GameTest(template = "empty", batch = "farm_reach_guard", timeoutTicks = 20)
    public static void farmTuningReach_ExceedingBlockBreakerReach_IsRejected(GameTestHelper helper) {
        helper.assertTrue(FarmTuning.REACH <= BlockBreaker.REACH,
                "Досягаемость фермы " + FarmTuning.REACH + " обязана быть не больше досягаемости ломки "
                        + BlockBreaker.REACH + ": иначе планировщик поставит дух туда, откуда BlockBreaker "
                        + "отвергнет обработку клетки");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "farm_registry", timeoutTicks = 20)
    public static void cropKinds_ExactIds_ExistInRegistries(GameTestHelper helper) {
        for (CropKind kind : CropKind.values()) {
            if (kind == CropKind.ANY) {
                continue;
            }
            Block block = CropRegistry.block(kind);
            Property<?> age = block.getStateDefinition().getProperty("age");
            helper.assertTrue(age instanceof IntegerProperty,
                    "у блока " + kind.blockId() + " обязано быть целое свойство age: по нему считается зрелость, "
                            + "а свойство найдено как " + age);
            int registryMax = ((IntegerProperty) age).getPossibleValues().stream().mapToInt(Integer::intValue).max()
                    .orElseThrow();
            helper.assertTrue(registryMax == kind.maxAge(),
                    "максимальный возраст " + kind + " в CropKind (" + kind.maxAge() + ") обязан совпасть с игрой ("
                            + registryMax + "): иначе зрелое сочтут недозрелым или наоборот");
            Item plantItem = CropRegistry.plantItem(kind);
            helper.assertTrue(plantItem instanceof BlockItem placing && placing.getBlock() == block,
                    "посадочный предмет " + kind.plantItem() + " обязан ставить блок " + kind.blockId()
                            + ": иначе посадка и пересадка посадят другой вид");
            CropRegistry.produceItem(kind);
        }
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "farm_view", timeoutTicks = 20)
    public static void levelFarmView_TileTable_ReadsExpectedTiles(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        List<TileCase> table = tileTable();
        for (int i = 0; i < table.size(); i++) {
            TileCase tileCase = table.get(i);
            helper.setBlock(new BlockPos(i % 10, GROUND_Y, i / 10 * 2), tileCase.ground());
            helper.setBlock(new BlockPos(i % 10, GROUND_Y + 1, i / 10 * 2), tileCase.above());
        }
        LevelFarmView view = new LevelFarmView(helper.getLevel());

        for (int i = 0; i < table.size(); i++) {
            TileCase tileCase = table.get(i);
            BlockPos absolute = helper.absolutePos(new BlockPos(i % 10, GROUND_Y, i / 10 * 2));
            FarmTile tile = view.tileAt(new BlockCoord(absolute.getX(), absolute.getY(), absolute.getZ()));
            helper.assertTrue(tile.equals(tileCase.expected()),
                    "клетка «" + tileCase.name() + "» обязана читаться как " + tileCase.expected() + ", а прочиталась "
                            + tile + ": по ней планировщик решает, что вспахать, собрать или не трогать");
        }
        helper.assertTrue(view.reads() == 2 * table.size(),
                "одна клетка читается двумя обращениями к миру (земля и верх): ожидалось " + 2 * table.size()
                        + ", фактически " + view.reads());
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "farm_targets_harvest", timeoutTicks = 100)
    public static void farmTargets_HarvestWheat_ListsGroundsOfWheatOnly(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        List<BlockPos> wheat = List.of(new BlockPos(2, GROUND_Y, 2), new BlockPos(3, GROUND_Y, 5));
        for (BlockPos ground : wheat) {
            helper.setBlock(ground, Blocks.FARMLAND);
            helper.setBlock(ground.above(), Blocks.WHEAT);
        }
        helper.setBlock(new BlockPos(6, GROUND_Y, 6), Blocks.FARMLAND);
        helper.setBlock(new BlockPos(6, GROUND_Y + 1, 6), Blocks.CARROTS);
        FarmTargets targets = new FarmTargets(helper.getLevel(), helper.absolutePos(new BlockPos(4, GROUND_Y, 4)), 6,
                FarmJob.HARVEST, CropKind.WHEAT);
        Set<BlockCoord> found = new HashSet<>();

        helper.onEachTick(() -> {
            if (!targets.done()) {
                found.addAll(targets.advance());
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(targets.done(), "поиск посевов в радиусе 6 обязан закончиться за несколько тиков");
            Set<BlockCoord> expected = new HashSet<>();
            for (BlockPos ground : wheat) {
                expected.add(coordOf(helper, ground));
            }
            helper.assertTrue(found.equals(expected),
                    "цели сбора пшеницы обязаны быть клетками земли под пшеницей (планировщик читает землю и верх), "
                            + "морковь не берётся: ожидалось " + expected + ", найдено " + found);
            helper.assertTrue(targets.maxSlicesPerTick() <= 4,
                    "поиск берёт не больше 4 срезов за тик, максимум за тик: " + targets.maxSlicesPerTick());
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "farm_targets_till", timeoutTicks = 100)
    public static void farmTargets_TillNearWater_WaterCountsOnlyWithinFour(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        BlockPos near = new BlockPos(5, GROUND_Y, 5);
        BlockPos far = new BlockPos(6, GROUND_Y, 5);
        helper.setBlock(near, Blocks.DIRT);
        helper.setBlock(far, Blocks.DIRT);
        helper.setBlock(new BlockPos(1, GROUND_Y, 5), Blocks.WATER);
        FarmTargets targets = new FarmTargets(helper.getLevel(), helper.absolutePos(new BlockPos(5, GROUND_Y, 4)), 6,
                FarmJob.TILL, CropKind.ANY);
        List<BlockCoord> found = new ArrayList<>();

        helper.onEachTick(() -> {
            if (!targets.done()) {
                found.addAll(targets.advance());
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(targets.done(), "поиск воды и земли в радиусе 6 обязан закончиться за несколько тиков");
            helper.assertTrue(targets.refusalReason() == null,
                    "малая лужа не должна давать отказ: " + targets.refusalReason());
            BlockCoord nearCoord = coordOf(helper, near);
            BlockCoord farCoord = coordOf(helper, far);
            helper.assertTrue(found.contains(nearCoord) && found.contains(farCoord),
                    "обе клетки земли обязаны быть целями вспашки, найдено " + found);
            helper.assertTrue(targets.hydration().watered(nearCoord),
                    "вода в 4 блоках по горизонтали на том же уровне увлажняет землю: вспашка обязана быть разрешена");
            helper.assertTrue(!targets.hydration().watered(farCoord),
                    "вода в 5 блоках землю не увлажняет: вспаханная там земля пересохнет, вспашка обязана быть "
                            + "запрещена");
            helper.assertTrue(targets.maxSlicesPerTick() <= 4,
                    "поиск берёт не больше 4 срезов за тик, максимум за тик: " + targets.maxSlicesPerTick());
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "farm_session_till", timeoutTicks = 110)
    public static void farmSession_TillFiveByFiveNearWater_TillsTwelveWearsHoeAndKeepsRest(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        for (int x = 3; x <= 7; x++) {
            for (int z = 3; z <= 7; z++) {
                helper.setBlock(new BlockPos(x, FLOOR_Y, z), Blocks.DIRT);
            }
        }
        helper.setBlock(new BlockPos(5, FLOOR_Y, 5), Blocks.WATER);
        SpiritEntity spirit = spawnWith(helper, new BlockPos(5, 1, 1), new ItemStack(Items.IRON_HOE));
        Map<BlockPos, Block> before = snapshot(helper);
        AtomicReference<FarmSession> session = drive(helper, spirit, ctx -> FarmSession.till(ctx, 4, 12));

        helper.succeedWhen(() -> {
            FarmSession run = finished(helper, session);
            helper.assertTrue(run.outcome() == FarmSession.Outcome.SUCCEEDED,
                    "двенадцать клеток земли в 4 блоках от воды обязаны вспахаться: " + run.outcome() + ", "
                            + run.failureReason());
            helper.assertTrue(countBlock(helper, Blocks.FARMLAND) == 12,
                    "обязано появиться ровно 12 пашни, фактически " + countBlock(helper, Blocks.FARMLAND));
            helper.assertTrue(spirit.getInventory().getItem(0).getDamageValue() == 12,
                    "износ мотыги обязан быть 12 (по единице на клетку, как у игрока), фактически "
                            + spirit.getInventory().getItem(0).getDamageValue());
            helper.assertTrue(changedBlocks(helper, before) == 12,
                    "кроме 12 вспаханных клеток мир обязан остаться прежним, изменилось " + changedBlocks(helper, before));
            helper.assertTrue(helper.getBlockState(new BlockPos(5, FLOOR_Y, 5)).is(Blocks.WATER),
                    "вода обязана остаться на месте");
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "farm_session_harvest", timeoutTicks = 250)
    public static void farmSession_HarvestMixedWheat_CollectsAndReplantsOnlyMature(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(new BlockPos(1, FLOOR_Y, 4), Blocks.WATER);
        List<BlockPos> cells = new ArrayList<>();
        for (int x = 3; x <= 5; x++) {
            for (int z = 3; z <= 5; z++) {
                cells.add(new BlockPos(x, FLOOR_Y, z));
            }
        }
        for (int i = 0; i < cells.size(); i++) {
            setCrop(helper, cells.get(i), CropKind.WHEAT, i % 2 == 0 ? 7 : 3);
        }
        SpiritEntity spirit = spawnWith(helper, new BlockPos(1, 1, 1), new ItemStack(Items.WHEAT_SEEDS, 5));
        AtomicReference<FarmSession> session = drive(helper, spirit,
                ctx -> FarmSession.harvestCrop(ctx, CropKind.WHEAT, 8, true));

        helper.succeedWhen(() -> {
            FarmSession run = finished(helper, session);
            helper.assertTrue(run.outcome() == FarmSession.Outcome.SUCCEEDED,
                    "пять зрелых клеток обязаны собраться, недозрелые не мешают: " + run.outcome() + ", "
                            + run.failureReason());
            for (int i = 0; i < cells.size(); i++) {
                int expectedAge = i % 2 == 0 ? 0 : 3;
                helper.assertTrue(cropAge(helper, cells.get(i)) == expectedAge,
                        "клетка " + i + " обязана иметь возраст " + expectedAge + " (зрелая пересажена, недозрелая нетронута), "
                                + "фактически " + cropAge(helper, cells.get(i)));
            }
            helper.assertTrue(countBlock(helper, Blocks.FARMLAND) == 9,
                    "пашни обязано остаться 9, фактически " + countBlock(helper, Blocks.FARMLAND));
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHEAT) == 5,
                    "с пяти зрелых клеток обязано быть собрано ровно 5 пшеницы, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHEAT));
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "farm_script_till", timeoutTicks = 120)
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
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "двенадцать клеток земли у воды обязаны вспахаться: статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            int farmland = 0;
            for (Map.Entry<BlockPos, Block> entry : before.entrySet()) {
                if (helper.getBlockState(entry.getKey()).is(Blocks.FARMLAND)) {
                    farmland++;
                    int dx = Math.abs(entry.getKey().getX() - water.getX());
                    int dz = Math.abs(entry.getKey().getZ() - water.getZ());
                    helper.assertTrue(dx <= 4 && dz <= 4,
                            "пашня " + entry.getKey() + " дальше 4 блоков от воды: она пересохнет");
                }
            }
            helper.assertTrue(farmland == 12, "обязано появиться ровно 12 пашни, фактически " + farmland);
            helper.assertTrue(spirit.getInventory().getItem(0).getDamageValue() == 12,
                    "износ мотыги обязан быть 12 (по единице на клетку, как у игрока), фактически "
                            + spirit.getInventory().getItem(0).getDamageValue());
            helper.assertTrue(changedBlocks(helper, before) == 12,
                    "кроме 12 вспаханных клеток мир обязан остаться прежним, изменилось "
                            + changedBlocks(helper, before));
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "farm_script_till_water", timeoutTicks = 400)
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
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "единственная годная клетка вспахивается, задача идёт до конца: статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(helper.getBlockState(new BlockPos(5, FLOOR_Y, 5)).is(Blocks.FARMLAND),
                    "земля в 4 блоках от воды обязана стать пашней");
            helper.assertTrue(helper.getBlockState(new BlockPos(6, FLOOR_Y, 5)).is(Blocks.DIRT),
                    "земля в 5 блоках от воды обязана остаться землёй: вспаханная там пашня пересохла бы");
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "farm_till_no_hoe", timeoutTicks = 100)
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
            helper.assertTrue(run.spiritTicks() <= 2,
                    "без мотыги отказ обязан случиться до первого действия, за 2 тика духа, заняло " + run.spiritTicks());
            helper.assertTrue(changedBlocks(helper, before) == 0,
                    "при отказе мир не меняется, изменилось блоков: " + changedBlocks(helper, before));
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "farm_till_hoe_breaks", timeoutTicks = 400)
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
            helper.assertTrue(run.spiritTicks() <= 2,
                    "изношенная мотыга не тратит последний удар: отказ обязан случиться до первого действия, за 2 тика "
                            + "духа, заняло " + run.spiritTicks());
            helper.assertTrue(countBlock(helper, Blocks.FARMLAND) == 0,
                    "ни одна клетка не вспахана изношенной мотыгой, фактически пашни "
                            + countBlock(helper, Blocks.FARMLAND));
            helper.assertTrue(spirit.getInventory().getItem(0).getDamageValue() == lastUseDamage,
                    "мотыга не должна изнашиваться дальше, фактический износ "
                            + spirit.getInventory().getItem(0).getDamageValue());
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "farm_till_weed", timeoutTicks = 400)
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
            helper.assertTrue(helper.getBlockState(new BlockPos(4, FLOOR_Y, 4)).is(Blocks.FARMLAND),
                    "земля под сорной травой обязана стать пашней");
            helper.assertTrue(helper.getBlockState(new BlockPos(4, FLOOR_Y + 1, 4)).isAir(),
                    "сорная трава обязана быть срублена рукой");
            helper.assertTrue(helper.getBlockState(new BlockPos(5, FLOOR_Y, 4)).is(Blocks.FARMLAND),
                    "чистая земля обязана стать пашней");
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "farm_till_stone", timeoutTicks = 200)
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
            helper.assertTrue(helper.getBlockState(new BlockPos(4, FLOOR_Y, 4)).is(Blocks.DIRT),
                    "земля под камнем не вспахивается");
            helper.assertTrue(helper.getBlockState(new BlockPos(4, FLOOR_Y + 1, 4)).is(Blocks.STONE),
                    "камень над землёй дух не ломает");
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "farm_plant_wheat", timeoutTicks = 120)
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
                helper.assertTrue(helper.getBlockState(cell.above()).is(Blocks.WHEAT) && cropAge(helper, cell) == 0,
                        "над пашней " + cell + " обязана расти пшеница возраста 0, а там "
                                + helper.getBlockState(cell.above()).getBlock());
            }
            int seeds = GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHEAT_SEEDS);
            helper.assertTrue(seeds == 1, "из 10 семян должно остаться ровно 1, фактически " + seeds);
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "farm_plant_no_seeds", timeoutTicks = 100)
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
            helper.assertTrue(run.spiritTicks() <= 2,
                    "без семян отказ обязан случиться до первого действия, за 2 тика духа, заняло " + run.spiritTicks());
            helper.assertTrue(changedBlocks(helper, before) == 0,
                    "при отказе мир не меняется, изменилось блоков: " + changedBlocks(helper, before));
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "farm_plant_seeds_run_out", timeoutTicks = 400)
    public static void plantCrop_SeedsRunOutAfterTwoCells_FailsNamingPlantedProgress(GameTestHelper helper) {
        arenaFloor(helper);
        helper.setBlock(WATER, Blocks.WATER);
        for (BlockPos cell : bed()) {
            helper.setBlock(cell, Blocks.FARMLAND);
        }
        SpiritEntity spirit = spawnWith(helper, START, new ItemStack(Items.WHEAT_SEEDS, 2));
        AtomicReference<FarmSession> session = drive(helper, spirit,
                ctx -> FarmSession.plantCrop(ctx, CropKind.WHEAT, 4, 4));

        helper.succeedWhen(() -> {
            FarmSession run = finished(helper, session);
            helper.assertTrue(run.outcome() == FarmSession.Outcome.FAILED,
                    "после двух посадок из двух семян сессия обязана завершиться провалом с понятным итогом: "
                            + run.outcome() + ", " + run.failureReason());
            String reason = run.failureReason() == null ? "" : run.failureReason();
            helper.assertTrue(reason.contains("посажено 2"),
                    "причина провала обязана назвать две посаженные клетки, фактически: " + reason);
            helper.assertTrue(reason.contains("нет семян"),
                    "причина провала обязана назвать кончившиеся семена, фактически: " + reason);
            helper.assertTrue(countBlock(helper, Blocks.WHEAT) == 2,
                    "посажено ровно две клетки, фактически пшеницы " + countBlock(helper, Blocks.WHEAT));
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "farm_plant_dark", timeoutTicks = 400)
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
        SpiritRun run = SpiritRun.launch(helper, spirit, plantCall(CropKind.WHEAT, 64), SETTLE_TICKS,
                () -> brightnessAbove(helper, lit) >= CROP_MIN_LIGHT && brightnessAbove(helper, dark) < CROP_MIN_LIGHT);

        helper.succeedWhen(() -> {
            run.assertStarted();
            int brightness = brightnessAbove(helper, dark);
            helper.assertTrue(brightness < CROP_MIN_LIGHT,
                    "подготовка: замурованная клетка обязана быть тёмной, а яркость " + brightness);
            assertFailedWith(helper, spirit, "не подходит: 1");
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(reason.contains("посажено 1 из 2"),
                    "причина обязана назвать одну посаженную клетку из двух, фактически: " + reason);
            helper.assertTrue(helper.getBlockState(lit.above()).is(Blocks.WHEAT),
                    "освещённая клетка обязана быть засажена");
            helper.assertTrue(helper.getBlockState(dark.above()).isAir(),
                    "в тёмной клетке посев не удержится: она обязана остаться пустой");
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHEAT_SEEDS) == 3,
                    "семя на тёмную клетку не списывается: из 4 должно остаться 3");
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "farm_harvest_wheat", timeoutTicks = 260)
    public static void harvestCrop_MixedWheat_HarvestsAndReplantsOnlyMature(GameTestHelper helper) {
        harvestMixed(helper, CropKind.WHEAT);
    }

    @GameTest(template = "arena", batch = "farm_harvest_carrot", timeoutTicks = 260)
    public static void harvestCrop_MixedCarrots_HarvestsAndReplantsOnlyMature(GameTestHelper helper) {
        harvestMixed(helper, CropKind.CARROT);
    }

    @GameTest(template = "arena", batch = "farm_harvest_potato", timeoutTicks = 250)
    public static void harvestCrop_MixedPotatoes_HarvestsAndReplantsOnlyMature(GameTestHelper helper) {
        harvestMixed(helper, CropKind.POTATO);
    }

    @GameTest(template = "arena", batch = "farm_harvest_beetroot", timeoutTicks = 270)
    public static void harvestCrop_MixedBeetroots_HarvestsAndReplantsOnlyMature(GameTestHelper helper) {
        harvestMixed(helper, CropKind.BEETROOT);
    }

    @GameTest(template = "arena", batch = "farm_harvest_filter", timeoutTicks = 50)
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
            helper.assertTrue(cropAge(helper, carrot) == 7 && helper.getBlockState(carrot.above()).is(Blocks.CARROTS),
                    "морковь при crop = wheat обязана остаться нетронутой и зрелой");
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.CARROT) == 0,
                    "морковь не должна попасть в инвентарь");
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "farm_harvest_no_replant", timeoutTicks = 400)
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
                helper.assertTrue(helper.getBlockState(cell.above()).isAir(),
                        "при replant = false клетка " + cell + " обязана остаться пустой, а там "
                                + helper.getBlockState(cell.above()).getBlock());
            }
            helper.assertTrue(countBlock(helper, Blocks.FARMLAND) == 9,
                    "пашни обязано остаться 9, фактически " + countBlock(helper, Blocks.FARMLAND));
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHEAT_SEEDS) >= 3,
                    "без пересадки семена только прибавляются: было 3, стало "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHEAT_SEEDS));
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "farm_bone_meal_grow", timeoutTicks = 400)
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
            helper.assertTrue(cropAge(helper, young) == 7,
                    "костная мука обязана довести недозрелую пшеницу до зрелости, возраст " + cropAge(helper, young));
            int spent = 16 - GameTestFixtures.countInInventory(spirit.getInventory(), Items.BONE_MEAL);
            helper.assertTrue(spent >= 2 && spent <= 4,
                    "возраст растёт на 2..5 за единицу, поэтому до зрелости хватит 2..4 единиц, потрачено " + spent);
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "farm_bone_meal_one", timeoutTicks = 400)
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
            helper.assertTrue(left == 4, "при count = 1 тратится ровно одна единица: из 5 остаться 4, осталось " + left);
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "farm_bone_meal_none", timeoutTicks = 100)
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
            helper.assertTrue(run.spiritTicks() <= 2,
                    "без муки отказ обязан случиться до первого действия, за 2 тика духа, заняло " + run.spiritTicks());
            helper.assertTrue(changedBlocks(helper, before) == 0 && cropAge(helper, new BlockPos(3, FLOOR_Y, 3)) == 0,
                    "при отказе мир не меняется");
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "farm_bone_meal_mature", timeoutTicks = 200)
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
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.BONE_MEAL) == 5,
                    "на зрелые посевы мука не тратится: обязано остаться 5, осталось "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.BONE_MEAL));
            GameTestFixtures.finish(helper);
        });
    }

    // [флак] замер 974 тика: посадка, подкормка и три сбора 9 клеток; потолок 1950 ≈ 2× замера
    @GameTest(template = "arena", batch = "farm_cycle", timeoutTicks = 1950)
    public static void farmCycle_PlantGrowHarvestThreeTimes_KeepsFarmlandAndSeeds(GameTestHelper helper) {
        arenaFloor(helper);
        helper.setBlock(WATER, Blocks.WATER);
        List<BlockPos> cells = bed();
        for (BlockPos cell : cells) {
            helper.setBlock(cell, Blocks.FARMLAND);
        }
        SpiritEntity spirit = spawnWith(helper, START, new ItemStack(Items.WHEAT_SEEDS, 17),
                new ItemStack(Items.BONE_MEAL, 64), new ItemStack(Items.BONE_MEAL, 64));
        List<TaskNode> steps = new ArrayList<>();
        steps.add(plantCall(CropKind.WHEAT, 64));
        for (int round = 0; round < 3; round++) {
            steps.add(boneMealCall(CropKind.WHEAT, 256));
            steps.add(harvestCall(CropKind.WHEAT, true));
        }
        SpiritRun.launch(helper, spirit, new SequenceNode(steps), SETTLE_TICKS);
        int seedsAfterPlanting = 17 - 9;

        helper.succeedWhen(() -> {
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "цикл посадка, подкормка и три сбора обязан завершиться успехом: статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            int seeds = GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHEAT_SEEDS);
            helper.assertTrue(seeds >= seedsAfterPlanting,
                    "после трёх кругов семян обязано быть не меньше, чем после посадки (" + seedsAfterPlanting
                            + "): самоподдерживающаяся грядка не теряет семян, а осталось " + seeds);
            helper.assertTrue(countBlock(helper, Blocks.FARMLAND) == 9,
                    "пашни обязано остаться 9, фактически " + countBlock(helper, Blocks.FARMLAND));
            for (BlockPos cell : cells) {
                helper.assertTrue(helper.getBlockState(cell.above()).is(Blocks.WHEAT),
                        "после цикла пустых клеток быть не должно, а над " + cell + " "
                                + helper.getBlockState(cell.above()).getBlock());
            }
            int wheat = GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHEAT);
            helper.assertTrue(wheat == 27, "за три сбора по 9 клеток обязано быть собрано 27 пшеницы, фактически "
                    + wheat);
            GameTestFixtures.finish(helper);
        });
    }

    // [флак] замер 278 тиков: падение с высоты и сбор 9 клеток с пересадкой; потолок 420 ≈ 1.5× замера
    @GameTest(template = "arena", batch = "farm_trample", timeoutTicks = 420)
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
            helper.assertTrue(countBlock(helper, Blocks.FARMLAND) == 9,
                    "дух упал на грядку с высоты и ходил по ней: пашни обязано остаться 9, осталось "
                            + countBlock(helper, Blocks.FARMLAND) + " (растоптанная пашня превращается в землю)");
            for (BlockPos cell : cells) {
                helper.assertTrue(cropAge(helper, cell) == 0, "каждая клетка обязана быть собрана и пересажена");
            }
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "farm_trample_repeat", timeoutTicks = 400)
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
                spirit.teleportTo(helper.absolutePos(cell).getX() + 0.5, cellY + 2.0,
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
            helper.assertTrue(drop.get() >= cells.size(),
                    "подготовка: дух обязан упасть на каждую из " + cells.size() + " клеток пашни, упал на "
                            + drop.get());
            int farmland = countBlock(helper, Blocks.FARMLAND);
            helper.assertTrue(farmland == cells.size(),
                    "дух упал с высоты на пашню " + cells.size() + " раз: пашня обязана остаться пашней, иначе "
                            + "растоптанная клетка превращается в землю и ферма в игре теряет грядку. Пашни "
                            + farmland + " из " + cells.size());
            for (BlockPos cell : cells) {
                helper.assertTrue(helper.getBlockState(cell).is(Blocks.FARMLAND),
                        "клетка " + cell + " обязана остаться пашней, а там "
                                + helper.getBlockState(cell).getBlock());
            }
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "farm_cancel", timeoutTicks = 150)
    public static void harvestCrop_CancelledMidWork_StopsWorldAndNavigation(GameTestHelper helper) {
        arenaFloor(helper);
        helper.setBlock(WATER, Blocks.WATER);
        for (BlockPos cell : bed()) {
            setCrop(helper, cell, CropKind.WHEAT, 7);
        }
        SpiritEntity spirit = spawnWith(helper, START, new ItemStack(Items.WHEAT_SEEDS, 3));
        SpiritRun run = launch(helper, spirit, harvestCall(CropKind.WHEAT, true));
        AtomicInteger cancelTick = new AtomicInteger(-1);
        AtomicReference<Map<BlockPos, Block>> frozen = new AtomicReference<>();
        AtomicInteger frozenWheat = new AtomicInteger();
        AtomicBoolean navigationStoppedAtCancel = new AtomicBoolean(false);

        helper.onEachTick(() -> {
            if (cancelTick.get() >= 0 || spirit.tickCount < SETTLE_TICKS) {
                return;
            }
            if (GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHEAT) >= 1
                    && !spirit.getNavigation().isDone()) {
                spirit.cancelTask();
                navigationStoppedAtCancel.set(spirit.getNavigation().isDone());
                frozen.set(snapshot(helper));
                frozenWheat.set(GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHEAT));
                cancelTick.set(spirit.tickCount);
            }
        });

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(cancelTick.get() >= 0 && spirit.tickCount >= cancelTick.get() + 40,
                    "нужно дождаться отмены и переждать 40 тиков: тик отмены " + cancelTick.get() + ", тик духа "
                            + spirit.tickCount);
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.CANCELLED,
                    "отменённая задача обязана быть CANCELLED, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            helper.assertTrue(changedBlocks(helper, frozen.get()) == 0,
                    "через 40 тиков после отмены мир обязан остаться прежним, изменилось блоков "
                            + changedBlocks(helper, frozen.get()));
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHEAT) == frozenWheat.get(),
                    "после отмены инвентарь не пополняется");
            helper.assertTrue(navigationStoppedAtCancel.get(),
                    "отмена в движении обязана остановить навигацию сразу, а не дать духу дойти до цели");
            GameTestFixtures.finish(helper);
        });
    }

    // [флак] полный инвентарь, путь до сундука и сбор двух клеток: под нагрузкой машины запас двойной.
    @GameTest(template = "arena", batch = "farm_full_inventory", timeoutTicks = 600)
    public static void harvestCrop_InventoryFullNearChest_DepositsAndContinues(GameTestHelper helper) {
        arenaFloor(helper);
        helper.setBlock(WATER, Blocks.WATER);
        setCrop(helper, new BlockPos(3, FLOOR_Y, 3), CropKind.WHEAT, 7);
        setCrop(helper, new BlockPos(5, FLOOR_Y, 5), CropKind.WHEAT, 7);
        helper.setBlock(new BlockPos(7, 1, 7), Blocks.CHEST);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), START);
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            spirit.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
        }
        SpiritRun run = launch(helper, spirit, harvestCall(CropKind.WHEAT, true));

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "при полном инвентаре дух обязан сдать лишнее на склад и продолжить сбор, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            var chest = helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(7, 1, 7)));
            helper.assertTrue(chest instanceof Container container
                            && GameTestFixtures.countInInventory(container, Items.DIRT) > 0,
                    "лишняя земля обязана уехать в сундук: иначе дух не освободил место и бросил сбор");
            int wheat = GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHEAT)
                    + GameTestFixtures.countInWorld(helper, Items.WHEAT)
                    + (chest instanceof Container container ? GameTestFixtures.countInInventory(container, Items.WHEAT) : 0);
            helper.assertTrue(wheat >= 2,
                    "после сдачи урожай обеих клеток обязан собраться (в инвентаре, в мире или в сундуке), фактически "
                            + wheat);
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "farm_deposit_harvest", timeoutTicks = 400)
    public static void harvestCrop_NearChest_DepositsHarvestAndKeepsSeedReserve(GameTestHelper helper) {
        arenaFloor(helper);
        helper.setBlock(WATER, Blocks.WATER);
        List<BlockPos> cells = bed();
        for (BlockPos cell : cells) {
            setCrop(helper, cell, CropKind.WHEAT, 7);
        }
        helper.setBlock(new BlockPos(7, 1, 7), Blocks.CHEST);
        SpiritEntity spirit = spawnWith(helper, START, new ItemStack(Items.WHEAT_SEEDS, 24));
        SpiritRun run = launch(helper, spirit, harvestCall(CropKind.WHEAT, false));

        helper.succeedWhen(() -> {
            run.assertStarted();
            assertSucceeded(helper, spirit, "зрелый урожай обязан собраться и уехать на склад");
            var chest = helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(7, 1, 7)));
            helper.assertTrue(chest instanceof Container container
                            && GameTestFixtures.countInInventory(container, Items.WHEAT) >= cells.size(),
                    "урожай обязан уехать в сундук склада: иначе фермер копит зерно у себя вместо сдачи");
            int seeds = GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHEAT_SEEDS);
            helper.assertTrue(seeds == 16,
                    "запас семян на посадку (16) обязан остаться у фермы, а излишек уехать на склад, фактически "
                            + seeds);
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "farm_persist", timeoutTicks = 400)
    public static void harvestCrop_SpiritReloadedMidWork_KeepsDoneAndFinishes(GameTestHelper helper) {
        arenaFloor(helper);
        helper.setBlock(WATER, Blocks.WATER);
        List<BlockPos> cells = bed();
        for (BlockPos cell : cells) {
            setCrop(helper, cell, CropKind.WHEAT, 7);
        }
        SpiritEntity spirit = spawnWith(helper, START, new ItemStack(Items.WHEAT_SEEDS, 3));
        AtomicReference<SpiritEntity> live = new AtomicReference<>(spirit);
        AtomicBoolean saved = new AtomicBoolean(false);
        AtomicInteger doneAfterReload = new AtomicInteger(-1);
        launch(helper, spirit, harvestCall(CropKind.WHEAT, true));

        helper.onEachTick(() -> {
            if (saved.get()) {
                return;
            }
            SpiritEntity current = live.get();
            if (GameTestFixtures.countInInventory(current.getInventory(), Items.WHEAT) >= 3) {
                saved.set(true);
                CompoundTag tag = new CompoundTag();
                current.saveWithoutId(tag);
                SpiritEntity reloaded = reload(helper, current, tag);
                live.set(reloaded);
                helper.runAfterDelay(2, () -> doneAfterReload.set(savedDone(helper, reloaded)));
            }
        });

        helper.succeedWhen(() -> {
            SpiritEntity current = live.get();
            helper.assertTrue(current.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "восстановленный сбор обязан дойти до конца: статус "
                            + current.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + current.taskFailureReason().orElse("нет"));
            helper.assertTrue(doneAfterReload.get() >= 3,
                    "после перезагрузки счётчик done обязан сохраниться (не меньше 3 собранных), а он "
                            + doneAfterReload.get());
            int wheat = GameTestFixtures.countInInventory(current.getInventory(), Items.WHEAT);
            helper.assertTrue(wheat == 9,
                    "каждая клетка собрана ровно раз: пшеницы обязано быть 9, а пересаженное не собрано повторно, "
                            + "фактически " + wheat);
            for (BlockPos cell : cells) {
                helper.assertTrue(cropAge(helper, cell) == 0, "каждая клетка обязана быть пересажена");
            }
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "farm_scale_harvest", timeoutTicks = 400)
    public static void farmSession_FullHarvestWithReplant_StaysWithinReadAndTickBudgets(GameTestHelper helper) {
        arenaFloor(helper);
        helper.setBlock(WATER, Blocks.WATER);
        List<BlockPos> cells = bed();
        for (BlockPos cell : cells) {
            setCrop(helper, cell, CropKind.WHEAT, 7);
        }
        SpiritEntity spirit = spawnWith(helper, START, new ItemStack(Items.WHEAT_SEEDS, 3));
        AtomicReference<FarmSession> session = drive(helper, spirit,
                ctx -> FarmSession.harvestCrop(ctx, CropKind.WHEAT, 8, true));

        helper.succeedWhen(() -> {
            FarmSession run = finished(helper, session);
            helper.assertTrue(run.outcome() == FarmSession.Outcome.SUCCEEDED,
                    "сбор девяти зрелых клеток обязан завершиться успехом: " + run.outcome() + ", "
                            + run.failureReason());
            helper.assertTrue(FarmPlanner.MAX_SAFETY_READS_PER_TARGET == 324,
                    "предел чтений безопасности на цель в production изменился с 324: пересмотри K в этом тесте");
            long viewBudget = (long) VIEW_READS_PER_CELL_PER_CALL * cells.size() * run.planCalls();
            helper.assertTrue(run.viewReads() <= viewBudget,
                    "чтений мира видом грядки обязано быть не больше " + viewBudget + " (K = "
                            + VIEW_READS_PER_CELL_PER_CALL + " на клетку на вызов планировщика), прочитано "
                            + run.viewReads() + " за " + run.planCalls() + " вызовов");
            long safetyBudget = (long) SAFETY_READS_PER_CELL_PER_CALL * cells.size() * run.planCalls();
            helper.assertTrue(run.safetyReads() <= safetyBudget,
                    "чтений безопасности обязано быть не больше " + safetyBudget + ", прочитано " + run.safetyReads());
            assertPerTickBudgets(helper, run);
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "farm_scale_till", timeoutTicks = 130)
    public static void farmSession_TillWithWeeds_StaysWithinPerTickBudgets(GameTestHelper helper) {
        arenaFloor(helper);
        helper.setBlock(WATER, Blocks.WATER);
        for (BlockPos cell : bed()) {
            helper.setBlock(cell, Blocks.DIRT);
            helper.setBlock(cell.above(), Blocks.SHORT_GRASS);
        }
        SpiritEntity spirit = spawnWith(helper, TILL_SPIRIT, new ItemStack(Items.IRON_HOE));
        AtomicReference<FarmSession> session = drive(helper, spirit, ctx -> FarmSession.till(ctx, 4, 9));

        helper.succeedWhen(() -> {
            FarmSession run = finished(helper, session);
            helper.assertTrue(run.outcome() == FarmSession.Outcome.SUCCEEDED,
                    "вспашка девяти клеток под травой обязана завершиться успехом: " + run.outcome() + ", "
                            + run.failureReason());
            helper.assertTrue(countBlock(helper, Blocks.FARMLAND) == 9, "обязано получиться 9 пашни");
            assertPerTickBudgets(helper, run);
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "farm_harvest_sweetberry", timeoutTicks = 300)
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
            assertSucceeded(helper, spirit, "пять зрелых кустов обязаны собраться PICK без ломки, недозрелые не мешают");
            for (int i = 0; i < cells.size(); i++) {
                int expectedAge = i % 2 == 0 ? 1 : 1;
                helper.assertTrue(cropAge(helper, cells.get(i)) == expectedAge,
                        "клетка " + i + " обязана иметь возраст 1 (зрелая собрана в 1, недозрелая 1 осталась), фактически "
                                + cropAge(helper, cells.get(i)));
                helper.assertTrue(helper.getBlockState(cells.get(i).above()).is(Blocks.SWEET_BERRY_BUSH),
                        "куст на клетке " + i + " обязан остаться: PICK не ломает блок");
            }
            helper.assertTrue(countBlock(helper, Blocks.FARMLAND) == 9,
                    "пашни обязано остаться 9, фактически " + countBlock(helper, Blocks.FARMLAND));
            int berries = GameTestFixtures.countInInventory(spirit.getInventory(), Items.SWEET_BERRIES);
            helper.assertTrue(berries >= 8,
                    "с пяти зрелых кустов обязано быть не меньше 8 ягод (2-3 с куста), фактически " + berries);
            int bushes = countBlock(helper, Blocks.SWEET_BERRY_BUSH);
            helper.assertTrue(bushes == 9, "кустов обязано остаться 9, фактически " + bushes);
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "farm_session_sweetberry", timeoutTicks = 300)
    public static void farmSession_HarvestSweetBerry_PicksAndCollectsWithoutBreaking(GameTestHelper helper) {
        arenaFloor(helper);
        helper.setBlock(WATER, Blocks.WATER);
        List<BlockPos> cells = bed();
        CropKind kind = CropKind.parse("sweet_berry_bush");
        for (int i = 0; i < cells.size(); i++) {
            setCrop(helper, cells.get(i), kind, i % 2 == 0 ? 3 : 1);
        }
        SpiritEntity spirit = spawnWith(helper, START);
        AtomicReference<FarmSession> session = drive(helper, spirit,
                ctx -> FarmSession.harvestCrop(ctx, kind, 8, true));

        helper.succeedWhen(() -> {
            FarmSession run = finished(helper, session);
            helper.assertTrue(run.outcome() == FarmSession.Outcome.SUCCEEDED,
                    "сбор кустов PICK обязан завершиться успехом: " + run.outcome() + ", " + run.failureReason());
            int berries = GameTestFixtures.countInInventory(spirit.getInventory(), Items.SWEET_BERRIES);
            helper.assertTrue(berries >= 8, "ягод не меньше 8, фактически " + berries);
            for (BlockPos cell : cells) {
                helper.assertTrue(helper.getBlockState(cell.above()).is(Blocks.SWEET_BERRY_BUSH),
                        "куст " + cell + " обязан остаться целым, а там " + helper.getBlockState(cell.above()).getBlock());
                helper.assertTrue(cropAge(helper, cell) == 1, "возраст после PICK обязан стать 1");
            }
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "farm_column_sugarcane", timeoutTicks = 400)
    public static void harvestCrop_SugarCaneColumn_LeavesBaseAndCollectsUpper(GameTestHelper helper) {
        columnHarvest(helper, CropKind.parse("sugar_cane"), Blocks.SUGAR_CANE, Blocks.SAND);
    }

    @GameTest(template = "arena", batch = "farm_column_bamboo", timeoutTicks = 400)
    public static void harvestCrop_BambooColumn_LeavesBaseAndCollectsUpper(GameTestHelper helper) {
        columnHarvest(helper, CropKind.parse("bamboo"), Blocks.BAMBOO, Blocks.DIRT);
    }

    @GameTest(template = "arena", batch = "farm_column_cactus", timeoutTicks = 400)
    public static void harvestCrop_CactusColumn_LeavesBaseAndCollectsUpper(GameTestHelper helper) {
        columnHarvest(helper, CropKind.parse("cactus"), Blocks.CACTUS, Blocks.SAND);
    }

    @GameTest(template = "arena", batch = "farm_column_cactus", timeoutTicks = 400)
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
            helper.assertTrue(helper.getBlockState(base.above()).is(Blocks.CACTUS),
                    "нижний кактус обязан остаться, а там " + helper.getBlockState(base.above()).getBlock());
            for (int level = 2; level <= 4; level++) {
                helper.assertTrue(helper.getBlockState(base.above(level)).isAir(),
                        "блок " + level + " столба обязан быть убран, а там "
                                + helper.getBlockState(base.above(level)).getBlock());
            }
            int count = GameTestFixtures.countInInventory(spirit.getInventory(), Items.CACTUS);
            helper.assertTrue(count == 3,
                    "с кактуса высотой 4 (нижний оставлен) обязано собраться ровно 3 кактуса: дроп не должен "
                            + "сгорать на нижнем кактусе, фактически " + count);
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "farm_column_single", timeoutTicks = 300)
    public static void harvestCrop_SingleCane_IsIgnoredAndBaseStays(GameTestHelper helper) {
        arenaFloor(helper);
        BlockPos ground = new BlockPos(4, FLOOR_Y, 4);
        helper.setBlock(ground, Blocks.SAND);
        helper.setBlock(ground.above(), Blocks.SUGAR_CANE);
        SpiritEntity spirit = spawnWith(helper, START);
        SpiritRun run = launch(helper, spirit, harvestCall(CropKind.parse("sugar_cane"), false));

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "одиночный тростник высотой 1 не цель: задача обязана провалиться, а статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет"));
            helper.assertTrue(helper.getBlockState(ground.above()).is(Blocks.SUGAR_CANE),
                    "одиночный тростник обязан остаться, а там " + helper.getBlockState(ground.above()).getBlock());
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.SUGAR_CANE) == 0,
                    "урожая с одиночного столба быть не должно");
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = MELON_BATCH, timeoutTicks = 300)
    public static void harvestCrop_MelonStemWithFruit_BreaksFruitKeepsStemAndFarmland(GameTestHelper helper) {
        fruitHarvest(helper, CropKind.MELON, Blocks.MELON_STEM, Blocks.MELON, Items.MELON_SLICE, 3,
                "плод арбуза даёт 3–7 ломтиков");
    }

    @GameTest(template = "arena", batch = PUMPKIN_BATCH, timeoutTicks = 300)
    public static void harvestCrop_PumpkinStemWithFruit_BreaksFruitKeepsStemAndFarmland(GameTestHelper helper) {
        fruitHarvest(helper, CropKind.PUMPKIN, Blocks.PUMPKIN_STEM, Blocks.PUMPKIN, Items.PUMPKIN, 1,
                "плод тыквы даёт одну тыкву");
    }

    private static void fruitHarvest(GameTestHelper helper, CropKind kind, Block stemBlock, Block fruitBlock,
            Item produce, int minProduce, String produceNote) {
        arenaFloor(helper);
        BlockPos ground = new BlockPos(4, FLOOR_Y, 4);
        setCrop(helper, ground, kind, kind.maxAge());
        BlockPos fruit = new BlockPos(5, FLOOR_Y + 1, 4);
        helper.setBlock(fruit, fruitBlock);
        SpiritEntity spirit = spawnWith(helper, START);
        SpiritRun run = launch(helper, spirit, harvestCall(kind, false));

        helper.succeedWhen(() -> {
            run.assertStarted();
            assertSucceeded(helper, spirit, "зрелый стебель " + kind + " с плодом обязан собрать плод");
            helper.assertTrue(helper.getBlockState(ground.above()).is(stemBlock),
                    "стебель " + kind + " обязан остаться целым, а там "
                            + helper.getBlockState(ground.above()).getBlock());
            helper.assertTrue(helper.getBlockState(ground).is(Blocks.FARMLAND),
                    "пашня под стеблем " + kind + " обязана остаться, а там " + helper.getBlockState(ground).getBlock());
            helper.assertTrue(helper.getBlockState(fruit).isAir(),
                    "плод " + kind + " обязан быть собран, а там " + helper.getBlockState(fruit).getBlock());
            int collected = GameTestFixtures.countInInventory(spirit.getInventory(), produce);
            helper.assertTrue(collected >= minProduce,
                    produceNote + ", фактически " + collected);
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = COCOA_BATCH, timeoutTicks = 300)
    public static void harvestCrop_CocoaOnJungleLog_CollectsAndReplantsOnSameSide(GameTestHelper helper) {
        arenaFloor(helper);
        BlockPos log = new BlockPos(4, FLOOR_Y + 1, 4);
        helper.setBlock(log, Blocks.JUNGLE_LOG);
        BlockPos cocoa = new BlockPos(5, FLOOR_Y + 1, 4);
        helper.setBlock(cocoa, Blocks.COCOA.defaultBlockState()
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
            helper.assertTrue(helper.getBlockState(log).is(Blocks.JUNGLE_LOG),
                    "ствол джунглей обязан остаться, а там " + helper.getBlockState(log).getBlock());
            helper.assertTrue(helper.getBlockState(cocoa).is(Blocks.COCOA),
                    "какао обязано быть пересажено на тот же бок ствола, а там "
                            + helper.getBlockState(cocoa).getBlock());
            helper.assertTrue(blockAge(helper, cocoa) <= 1,
                    "пересаженный боб моложе спелого: возраст " + blockAge(helper, cocoa)
                            + ", а исходное спелое какао было возраста " + CropKind.COCOA.maxAge());
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.COCOA_BEANS) >= 1,
                    "собранные бобы минус один пересаженный обязаны остаться в инвентаре, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COCOA_BEANS));
            GameTestFixtures.finish(helper);
        });
    }

    private static void columnHarvest(GameTestHelper helper, CropKind kind, Block columnBlock, Block groundBlock) {
        arenaFloor(helper);
        List<BlockPos> bases = List.of(new BlockPos(3, FLOOR_Y, 3), new BlockPos(5, FLOOR_Y, 5));
        for (BlockPos base : bases) {
            helper.setBlock(base, groundBlock);
            if (kind == CropKind.parse("cactus")) {
                helper.setBlock(base.above(), columnBlock);
                helper.setBlock(base.above().above(), columnBlock);
                helper.setBlock(base.above().above().above(), columnBlock);
            } else if (kind == CropKind.parse("sugar_cane")) {
                helper.setBlock(base.below(), Blocks.STONE);
                helper.setBlock(base, groundBlock);
                helper.setBlock(new BlockPos(base.getX() + 1, FLOOR_Y, base.getZ()), Blocks.WATER);
                setColumn(helper, base, columnBlock, 3);
            } else {
                setColumn(helper, base, columnBlock, 3);
            }
        }
        SpiritEntity spirit = spawnWith(helper, START);
        SpiritRun run = launch(helper, spirit, harvestCall(kind, false));

        helper.succeedWhen(() -> {
            run.assertStarted();
            assertSucceeded(helper, spirit, "столб " + kind + " обязан собраться, нижний блок остаётся");
            for (BlockPos base : bases) {
                helper.assertTrue(helper.getBlockState(base.above()).is(columnBlock),
                        "нижний блок столба " + kind + " на " + base + " обязан остаться: BREAK_COLUMN не ломает основание, а там "
                                + helper.getBlockState(base.above()).getBlock());
                helper.assertTrue(helper.getBlockState(base.above().above()).isAir(),
                        "верх столба обязан быть убран: второй блок и выше собираются, а там "
                                + helper.getBlockState(base.above().above()).getBlock());
                if (kind != CropKind.parse("cactus")) {
                    helper.assertTrue(helper.getBlockState(base.above().above().above()).isAir(),
                            "третий блок столба тоже убран");
                }
            }
            int count = GameTestFixtures.countInInventory(spirit.getInventory(),
                    kind == CropKind.parse("sugar_cane") ? Items.SUGAR_CANE
                            : kind == CropKind.parse("bamboo") ? Items.BAMBOO : Items.CACTUS);
            helper.assertTrue(count >= 4, "с двух столбов по 3 блока (оставлен 1) добыча не меньше 4, фактически " + count);
            GameTestFixtures.finish(helper);
        });
    }

    private static void setColumn(GameTestHelper helper, BlockPos ground, Block block, int height) {
        for (int i = 1; i <= height; i++) {
            helper.setBlock(ground.above(i), block);
        }
    }

    private static void harvestMixed(GameTestHelper helper, CropKind kind) {
        arenaFloor(helper);
        helper.setBlock(WATER, Blocks.WATER);
        List<BlockPos> cells = bed();
        int matureAge = kind.maxAge();
        int youngAge = kind == CropKind.BEETROOT ? 1 : 3;
        for (int i = 0; i < cells.size(); i++) {
            setCrop(helper, cells.get(i), kind, i % 2 == 0 ? matureAge : youngAge);
        }
        Item plantItem = CropRegistry.plantItem(kind);
        Item produceItem = CropRegistry.produceItem(kind);
        int initial = 6;
        SpiritEntity spirit = spawnWith(helper, START, new ItemStack(plantItem, initial));
        SpiritRun run = launch(helper, spirit, harvestCall(kind, true));

        helper.succeedWhen(() -> {
            run.assertStarted();
            assertSucceeded(helper, spirit, "пять зрелых клеток " + kind + " обязаны собраться, недозрелые не мешают");
            for (int i = 0; i < cells.size(); i++) {
                int expectedAge = i % 2 == 0 ? 0 : youngAge;
                helper.assertTrue(cropAge(helper, cells.get(i)) == expectedAge,
                        "клетка " + i + " обязана иметь возраст " + expectedAge
                                + " (зрелая пересажена, недозрелая нетронута), фактически "
                                + cropAge(helper, cells.get(i)));
            }
            helper.assertTrue(countBlock(helper, Blocks.FARMLAND) == 9,
                    "пашни обязано остаться 9, фактически " + countBlock(helper, Blocks.FARMLAND));
            int produce = GameTestFixtures.countInInventory(spirit.getInventory(), produceItem);
            int plant = GameTestFixtures.countInInventory(spirit.getInventory(), plantItem);
            if (produceItem == plantItem) {
                helper.assertTrue(plant >= initial,
                        "посадочный предмет и урожай — один предмет: после пересадки пяти клеток не меньше исходных "
                                + initial + ", фактически " + plant);
            } else {
                helper.assertTrue(produce >= 5, "урожая обязано быть не меньше 5, фактически " + produce);
                helper.assertTrue(plant >= initial - 5,
                        "семян не меньше исходных за вычетом пяти пересаженных (" + (initial - 5)
                                + "), фактически " + plant);
            }
            GameTestFixtures.reset(helper);
        });
    }

    private static void assertPerTickBudgets(GameTestHelper helper, FarmSession run) {
        helper.assertTrue(run.maxSlicesPerTick() <= 4,
                "за тик поиск берёт не больше 4 срезов, максимум за тик: " + run.maxSlicesPerTick());
        helper.assertTrue(run.maxActionsPerTick() <= 1,
                "за тик скрипт делает не больше одного действия (удар, вспашка, посадка, мука), максимум за тик: "
                        + run.maxActionsPerTick());
        helper.assertTrue(run.maxEntityQueriesPerTick() <= 1,
                "за тик запрос сущностей не больше одного, максимум за тик: " + run.maxEntityQueriesPerTick());
    }

    private static int savedDone(GameTestHelper helper, SpiritEntity spirit) {
        Optional<String> json = spirit.taskSnapshot();
        helper.assertTrue(json.isPresent(), "у восстановленного духа обязан быть снимок задачи");
        TaskProgress progress = TaskSnapshotCodec.decode(json.get()).progress();
        helper.assertTrue(progress instanceof TaskProgress.ScriptAt,
                "снимок одиночного скрипта обязан нести состояние скрипта, а там " + progress);
        Object done = ((TaskProgress.ScriptAt) progress).state().get("done");
        helper.assertTrue(done instanceof Number, "в состоянии скрипта обязан быть счётчик done, а там " + done);
        return ((Number) done).intValue();
    }

    private static SpiritEntity reload(GameTestHelper helper, SpiritEntity original, CompoundTag tag) {
        original.discard();
        SpiritEntity reloaded = ModEntities.SPIRIT.get().create(helper.getLevel());
        reloaded.load(tag);
        if (!helper.getLevel().addFreshEntity(reloaded)) {
            throw new IllegalStateException("перезагруженный дух не добавлен в мир: uuid " + reloaded.getUUID());
        }
        return reloaded;
    }

    private static int brightnessAbove(GameTestHelper helper, BlockPos ground) {
        return helper.getLevel().getRawBrightness(helper.absolutePos(ground.above()), 0);
    }

    private static void arenaFloor(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
    }

    private static void fillGround(GameTestHelper helper, Block block) {
        for (BlockPos cell : bed()) {
            helper.setBlock(cell, block);
        }
    }

    private static List<BlockPos> bed() {
        List<BlockPos> cells = new ArrayList<>();
        for (int x = 3; x <= 5; x++) {
            for (int z = 3; z <= 5; z++) {
                cells.add(new BlockPos(x, FLOOR_Y, z));
            }
        }
        return cells;
    }

    private static SpiritRun launch(GameTestHelper helper, SpiritEntity spirit, TaskNode call) {
        return SpiritRun.launch(helper, spirit, call, SETTLE_TICKS);
    }

    private static void assertSucceeded(GameTestHelper helper, SpiritEntity spirit, String expectation) {
        helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                expectation + ": статус " + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи")
                        + ", причина " + spirit.taskFailureReason().orElse("нет"));
    }

    private static void assertFailedWith(GameTestHelper helper, SpiritEntity spirit, String fragment) {
        helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                "задача обязана провалиться, а статус " + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
        String reason = spirit.taskFailureReason().orElse("");
        helper.assertTrue(reason.contains(fragment),
                "причина провала обязана содержать «" + fragment + "», фактически: " + reason);
    }

    private static ScriptCall plantCall(CropKind crop, int count) {
        return new ScriptCall("plant_crop", Map.of("crop", name(crop), "radius", 8, "count", count));
    }

    private static ScriptCall harvestCall(CropKind crop, boolean replant) {
        return new ScriptCall("harvest_crop", Map.of("crop", name(crop), "radius", 8, "replant", replant));
    }

    private static ScriptCall boneMealCall(CropKind crop, int count) {
        return new ScriptCall("bone_meal", Map.of("crop", name(crop), "radius", 8, "count", count));
    }

    private static String name(CropKind crop) {
        return crop.name().toLowerCase(Locale.ROOT);
    }

    private static ScriptCall tillCall(int radius, int count) {
        return new ScriptCall("till", Map.of("radius", radius, "count", count));
    }

    @GameTest(template = "arena", batch = "farm_walled", timeoutTicks = 300)
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
            helper.assertTrue(helper.getBlockState(cell.above()).isAir(),
                    "дух заперт на пашне, предпочтённая стоянка недостижима, но зрелая клетка в досягаемости: когда "
                            + "навигация не доводит духа до стоянки, работа обязана идти оттуда, где он стоит, а не "
                            + "считаться «без места для духа». Статус " + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            GameTestFixtures.finish(helper);
        });
    }

    private static SpiritEntity spawnWith(GameTestHelper helper, BlockPos relative, ItemStack... stacks) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), relative);
        for (int slot = 0; slot < stacks.length; slot++) {
            spirit.getInventory().setItem(slot, stacks[slot]);
        }
        return spirit;
    }

    private static AtomicReference<FarmSession> drive(GameTestHelper helper, SpiritEntity spirit,
            Function<SpiritEntity, FarmSession> factory) {
        AtomicReference<FarmSession> session = new AtomicReference<>();
        helper.onEachTick(() -> {
            FarmSession current = session.get();
            if (current == null) {
                if (spirit.tickCount >= SETTLE_TICKS) {
                    session.set(factory.apply(spirit));
                }
                return;
            }
            if (current.outcome() == FarmSession.Outcome.RUNNING) {
                current.tick();
            }
        });
        return session;
    }

    private static FarmSession finished(GameTestHelper helper, AtomicReference<FarmSession> session) {
        FarmSession run = session.get();
        helper.assertTrue(run != null && run.outcome() != FarmSession.Outcome.RUNNING,
                "сессия обязана завершиться, а не висеть в RUNNING: "
                        + (run == null ? "сессия не стартовала" : run.outcome().name()));
        return run;
    }

    private static void setCrop(GameTestHelper helper, BlockPos ground, CropKind kind, int age) {
        helper.setBlock(ground, Blocks.FARMLAND);
        Block block = CropRegistry.block(kind);
        IntegerProperty ageProperty = (IntegerProperty) block.getStateDefinition().getProperty("age");
        helper.setBlock(ground.above(), block.defaultBlockState().setValue(ageProperty, age));
    }

    private static int cropAge(GameTestHelper helper, BlockPos ground) {
        return blockAge(helper, ground.above());
    }

    private static int blockAge(GameTestHelper helper, BlockPos pos) {
        BlockState state = helper.getBlockState(pos);
        Property<?> age = state.getBlock().getStateDefinition().getProperty("age");
        helper.assertTrue(age instanceof IntegerProperty, "в " + pos + " обязан быть блок с возрастом, а там "
                + state.getBlock());
        return state.getValue((IntegerProperty) age);
    }

    private static Map<BlockPos, Block> snapshot(GameTestHelper helper) {
        Map<BlockPos, Block> blocks = new HashMap<>();
        for (int x = 0; x < ARENA_SIZE; x++) {
            for (int y = 0; y < ARENA_HEIGHT; y++) {
                for (int z = 0; z < ARENA_SIZE; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    blocks.put(pos, helper.getBlockState(pos).getBlock());
                }
            }
        }
        return blocks;
    }

    private static int changedBlocks(GameTestHelper helper, Map<BlockPos, Block> before) {
        int changed = 0;
        for (Map.Entry<BlockPos, Block> entry : before.entrySet()) {
            if (helper.getBlockState(entry.getKey()).getBlock() != entry.getValue()) {
                changed++;
            }
        }
        return changed;
    }

    private static int countBlock(GameTestHelper helper, Block block) {
        int total = 0;
        for (int x = 0; x < ARENA_SIZE; x++) {
            for (int y = 0; y < ARENA_HEIGHT; y++) {
                for (int z = 0; z < ARENA_SIZE; z++) {
                    if (helper.getBlockState(new BlockPos(x, y, z)).is(block)) {
                        total++;
                    }
                }
            }
        }
        return total;
    }

    private static BlockCoord coordOf(GameTestHelper helper, BlockPos relative) {
        BlockPos absolute = helper.absolutePos(relative);
        return new BlockCoord(absolute.getX(), absolute.getY(), absolute.getZ());
    }

    private static List<TileCase> tileTable() {
        List<TileCase> table = new ArrayList<>();
        table.add(plain("грязь, воздух", Blocks.DIRT, Blocks.AIR, Ground.DIRT, Above.AIR));
        table.add(plain("дёрн, воздух", Blocks.GRASS_BLOCK, Blocks.AIR, Ground.GRASS, Above.AIR));
        table.add(plain("тропа, воздух", Blocks.DIRT_PATH, Blocks.AIR, Ground.PATH, Above.AIR));
        table.add(plain("пашня, воздух", Blocks.FARMLAND, Blocks.AIR, Ground.FARMLAND, Above.AIR));
        table.add(plain("камень, воздух", Blocks.STONE, Blocks.AIR, Ground.OTHER, Above.AIR));
        table.add(plain("грязь, трава", Blocks.DIRT, Blocks.SHORT_GRASS, Ground.DIRT, Above.WEED));
        table.add(plain("грязь, папоротник", Blocks.DIRT, Blocks.FERN, Ground.DIRT, Above.WEED));
        table.add(plain("грязь, мёртвый куст", Blocks.DIRT, Blocks.DEAD_BUSH, Ground.DIRT, Above.WEED));
        table.add(plain("грязь, факел (твёрдость 0, но не растение)", Blocks.DIRT, Blocks.TORCH, Ground.DIRT,
                Above.BLOCKED));
        table.add(plain("грязь, снежный слой (замещаем, но твёрдость 0.1)", Blocks.DIRT, Blocks.SNOW, Ground.DIRT,
                Above.BLOCKED));
        table.add(plain("грязь, камень", Blocks.DIRT, Blocks.STONE, Ground.DIRT, Above.BLOCKED));
        table.add(plain("грязь, вода", Blocks.DIRT, Blocks.WATER, Ground.DIRT, Above.BLOCKED));
        table.add(plain("пашня, плод арбуза (не посев)", Blocks.FARMLAND, Blocks.MELON, Ground.FARMLAND,
                Above.BLOCKED));
        for (int age = 0; age <= 7; age++) {
            table.add(crop(CropKind.WHEAT, age));
            table.add(crop(CropKind.CARROT, age));
            table.add(crop(CropKind.POTATO, age));
        }
        for (int age = 0; age <= 3; age++) {
            table.add(crop(CropKind.BEETROOT, age));
            table.add(crop(CropKind.parse("sweet_berry_bush"), age));
        }
        return table;
    }

    private static TileCase plain(String name, Block ground, Block above, Ground expectedGround, Above expectedAbove) {
        return new TileCase(name, ground, above.defaultBlockState(),
                new FarmTile(expectedGround, expectedAbove, Optional.empty()));
    }

    private static TileCase crop(CropKind kind, int age) {
        Block block = CropRegistry.block(kind);
        IntegerProperty ageProperty = (IntegerProperty) block.getStateDefinition().getProperty("age");
        return new TileCase(kind + " возраст " + age, Blocks.FARMLAND,
                block.defaultBlockState().setValue(ageProperty, age),
                new FarmTile(Ground.FARMLAND, Above.CROP, Optional.of(new CropState(kind, age))));
    }

    private record TileCase(String name, Block ground, BlockState above, FarmTile expected) {
    }
}
