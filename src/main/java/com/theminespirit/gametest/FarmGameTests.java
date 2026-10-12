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
import com.theminespirit.neoforge.FarmSession;
import com.theminespirit.neoforge.FarmTargets;
import com.theminespirit.neoforge.LevelFarmView;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
    private static final int FLOOR_Y = 0;
    private static final int SETTLE_TICKS = 5;
    private static final int ARENA_SIZE = 11;
    private static final int ARENA_HEIGHT = 8;

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

    @GameTest(template = "arena", batch = "farm_session_till", timeoutTicks = 600)
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
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "arena", batch = "farm_session_harvest", timeoutTicks = 800)
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
            GameTestFixtures.reset(helper);
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
        BlockState state = helper.getBlockState(ground.above());
        Property<?> age = state.getBlock().getStateDefinition().getProperty("age");
        helper.assertTrue(age instanceof IntegerProperty, "над клеткой " + ground + " обязан расти посев, а там "
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
