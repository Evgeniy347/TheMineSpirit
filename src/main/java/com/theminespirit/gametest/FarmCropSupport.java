package com.theminespirit.gametest;

import static com.theminespirit.gametest.FarmTestSupport.ARENA_HEIGHT;
import static com.theminespirit.gametest.FarmTestSupport.ARENA_SIZE;
import static com.theminespirit.gametest.FarmTestSupport.FLOOR_Y;
import static com.theminespirit.gametest.FarmTestSupport.START;
import static com.theminespirit.gametest.FarmTestSupport.WATER;
import static com.theminespirit.gametest.FarmTestSupport.arenaFloor;
import static com.theminespirit.gametest.FarmTestSupport.assertSucceeded;
import static com.theminespirit.gametest.FarmTestSupport.bed;
import static com.theminespirit.gametest.FarmTestSupport.harvestCall;
import static com.theminespirit.gametest.FarmTestSupport.launch;
import static com.theminespirit.gametest.FarmTestSupport.name;
import static com.theminespirit.gametest.FarmTestSupport.spawnWith;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.farming.Above;
import com.theminespirit.core.farming.CropKind;
import com.theminespirit.core.farming.CropState;
import com.theminespirit.core.farming.FarmTile;
import com.theminespirit.core.farming.Ground;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.CropRegistry;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
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
final class FarmCropSupport {
    private FarmCropSupport() {}

    static List<TileCase> tileTable() {
        List<TileCase> table = new ArrayList<>();
        table.add(plain("грязь, воздух", Blocks.DIRT, Blocks.AIR, Ground.DIRT, Above.AIR));
        table.add(plain("дёрн, воздух", Blocks.GRASS_BLOCK, Blocks.AIR, Ground.GRASS, Above.AIR));
        table.add(plain("тропа, воздух", Blocks.DIRT_PATH, Blocks.AIR, Ground.PATH, Above.AIR));
        table.add(plain("пашня, воздух", Blocks.FARMLAND, Blocks.AIR, Ground.FARMLAND, Above.AIR));
        table.add(plain("камень, воздух", Blocks.STONE, Blocks.AIR, Ground.OTHER, Above.AIR));
        table.add(plain("грязь, трава", Blocks.DIRT, Blocks.SHORT_GRASS, Ground.DIRT, Above.WEED));
        table.add(plain("грязь, папоротник", Blocks.DIRT, Blocks.FERN, Ground.DIRT, Above.WEED));
        table.add(plain("грязь, мёртвый куст", Blocks.DIRT, Blocks.DEAD_BUSH, Ground.DIRT, Above.WEED));
        table.add(plain(
                "грязь, факел (твёрдость 0, но не растение)", Blocks.DIRT, Blocks.TORCH, Ground.DIRT, Above.BLOCKED));
        table.add(plain(
                "грязь, снежный слой (замещаем, но твёрдость 0.1)",
                Blocks.DIRT,
                Blocks.SNOW,
                Ground.DIRT,
                Above.BLOCKED));
        table.add(plain("грязь, камень", Blocks.DIRT, Blocks.STONE, Ground.DIRT, Above.BLOCKED));
        table.add(plain("грязь, вода", Blocks.DIRT, Blocks.WATER, Ground.DIRT, Above.BLOCKED));
        table.add(
                plain("пашня, плод арбуза (не посев)", Blocks.FARMLAND, Blocks.MELON, Ground.FARMLAND, Above.BLOCKED));
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

    static TileCase plain(String name, Block ground, Block above, Ground expectedGround, Above expectedAbove) {
        return new TileCase(
                name, ground, above.defaultBlockState(), new FarmTile(expectedGround, expectedAbove, Optional.empty()));
    }

    static TileCase crop(CropKind kind, int age) {
        Block block = CropRegistry.block(kind);
        IntegerProperty ageProperty =
                (IntegerProperty) block.getStateDefinition().getProperty("age");
        return new TileCase(
                kind + " возраст " + age,
                Blocks.FARMLAND,
                block.defaultBlockState().setValue(ageProperty, age),
                new FarmTile(Ground.FARMLAND, Above.CROP, Optional.of(new CropState(kind, age))));
    }

    static void setCrop(GameTestHelper helper, BlockPos ground, CropKind kind, int age) {
        helper.setBlock(ground, Blocks.FARMLAND);
        Block block = CropRegistry.block(kind);
        IntegerProperty ageProperty =
                (IntegerProperty) block.getStateDefinition().getProperty("age");
        helper.setBlock(ground.above(), block.defaultBlockState().setValue(ageProperty, age));
    }

    static int cropAge(GameTestHelper helper, BlockPos ground) {
        return blockAge(helper, ground.above());
    }

    static int blockAge(GameTestHelper helper, BlockPos pos) {
        BlockState state = helper.getBlockState(pos);
        Property<?> age = state.getBlock().getStateDefinition().getProperty("age");
        helper.assertTrue(
                age instanceof IntegerProperty,
                "в " + pos + " обязан быть блок с возрастом, а там " + state.getBlock());
        return state.getValue((IntegerProperty) age);
    }

    static int countBlock(GameTestHelper helper, Block block) {
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

    static BlockCoord coordOf(GameTestHelper helper, BlockPos relative) {
        BlockPos absolute = helper.absolutePos(relative);
        return new BlockCoord(absolute.getX(), absolute.getY(), absolute.getZ());
    }

    static void fruitHarvest(
            GameTestHelper helper,
            CropKind kind,
            Block stemBlock,
            Block fruitBlock,
            Item produce,
            int minProduce,
            String produceNote) {
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
            helper.assertTrue(
                    helper.getBlockState(ground.above()).is(stemBlock),
                    "стебель " + kind + " обязан остаться целым, а там "
                            + helper.getBlockState(ground.above()).getBlock());
            helper.assertTrue(
                    helper.getBlockState(ground).is(Blocks.FARMLAND),
                    "пашня под стеблем " + kind + " обязана остаться, а там "
                            + helper.getBlockState(ground).getBlock());
            helper.assertTrue(
                    helper.getBlockState(fruit).isAir(),
                    "плод " + kind + " обязан быть собран, а там "
                            + helper.getBlockState(fruit).getBlock());
            int collected = GameTestFixtures.countInInventory(spirit.getInventory(), produce);
            helper.assertTrue(collected >= minProduce, produceNote + ", фактически " + collected);
            GameTestFixtures.finish(helper);
        });
    }

    static void columnHarvest(GameTestHelper helper, CropKind kind, Block columnBlock, Block groundBlock) {
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
                helper.assertTrue(
                        helper.getBlockState(base.above()).is(columnBlock),
                        "нижний блок столба " + kind + " на " + base
                                + " обязан остаться: BREAK_COLUMN не ломает основание, а там "
                                + helper.getBlockState(base.above()).getBlock());
                helper.assertTrue(
                        helper.getBlockState(base.above().above()).isAir(),
                        "верх столба обязан быть убран: второй блок и выше собираются, а там "
                                + helper.getBlockState(base.above().above()).getBlock());
                if (kind != CropKind.parse("cactus")) {
                    helper.assertTrue(
                            helper.getBlockState(base.above().above().above()).isAir(),
                            "третий блок столба тоже убран");
                }
            }
            int count = GameTestFixtures.countInInventory(
                    spirit.getInventory(),
                    kind == CropKind.parse("sugar_cane")
                            ? Items.SUGAR_CANE
                            : kind == CropKind.parse("bamboo") ? Items.BAMBOO : Items.CACTUS);
            helper.assertTrue(
                    count >= 4, "с двух столбов по 3 блока (оставлен 1) добыча не меньше 4, фактически " + count);
            GameTestFixtures.finish(helper);
        });
    }

    static void setColumn(GameTestHelper helper, BlockPos ground, Block block, int height) {
        for (int i = 1; i <= height; i++) {
            helper.setBlock(ground.above(i), block);
        }
    }

    static void harvestMixed(GameTestHelper helper, CropKind kind) {
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
                helper.assertTrue(
                        cropAge(helper, cells.get(i)) == expectedAge,
                        "клетка " + i + " обязана иметь возраст " + expectedAge
                                + " (зрелая пересажена, недозрелая нетронута), фактически "
                                + cropAge(helper, cells.get(i)));
            }
            helper.assertTrue(
                    countBlock(helper, Blocks.FARMLAND) == 9,
                    "пашни обязано остаться 9, фактически " + countBlock(helper, Blocks.FARMLAND));
            int produce = GameTestFixtures.countInInventory(spirit.getInventory(), produceItem);
            int plant = GameTestFixtures.countInInventory(spirit.getInventory(), plantItem);
            if (produceItem.equals(plantItem)) {
                helper.assertTrue(
                        plant >= initial,
                        "посадочный предмет и урожай — один предмет: после пересадки пяти клеток не меньше исходных "
                                + initial + ", фактически " + plant);
            } else {
                helper.assertTrue(
                        produce == 5,
                        "с пяти зрелых клеток обязано быть собрано ровно 5 урожая, фактически " + produce);
                helper.assertTrue(
                        plant >= initial - 5,
                        "семян не меньше исходных за вычетом пяти пересаженных (" + (initial - 5) + "), фактически "
                                + plant);
            }
            GameTestFixtures.reset(helper);
        });
    }
}
