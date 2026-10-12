package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.farming.Above;
import com.theminespirit.core.farming.CropKind;
import com.theminespirit.core.farming.CropState;
import com.theminespirit.core.farming.FarmJob;
import com.theminespirit.core.farming.FarmTile;
import com.theminespirit.core.farming.Ground;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.CropRegistry;
import com.theminespirit.neoforge.FarmTargets;
import com.theminespirit.neoforge.LevelFarmView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FarmGameTests {
    private static final int GROUND_Y = 1;

    private FarmGameTests() {
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
        GameTestFixtures.reset(helper);
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
            GameTestFixtures.reset(helper);
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
            GameTestFixtures.reset(helper);
        });
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
        table.add(plain("пашня, стебель арбуза (чужой посев)", Blocks.FARMLAND, Blocks.MELON_STEM, Ground.FARMLAND,
                Above.BLOCKED));
        for (int age = 0; age <= 7; age++) {
            table.add(crop(CropKind.WHEAT, age));
            table.add(crop(CropKind.CARROT, age));
            table.add(crop(CropKind.POTATO, age));
        }
        for (int age = 0; age <= 3; age++) {
            table.add(crop(CropKind.BEETROOT, age));
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
