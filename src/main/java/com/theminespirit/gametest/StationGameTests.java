package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.crafting.CraftTuning;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.crafting.StationFinder;
import com.theminespirit.neoforge.crafting.StationPlacer;
import com.theminespirit.neoforge.crafting.StationType;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StationGameTests {
    private static final String FIELD = "arena";
    private static final BlockPos SPIRIT_CELL = new BlockPos(5, 1, 5);
    private static final int SEARCH_TIMEOUT = 100;
    private static final int PLACE_TIMEOUT = 40;

    private StationGameTests() {}

    @GameTest(template = FIELD, batch = "station_finder", timeoutTicks = SEARCH_TIMEOUT)
    public static void finder_TwoTables_FindsTheNearestOne(GameTestHelper helper) {
        prepareField(helper);
        helper.setBlock(3, 1, 3, Blocks.CRAFTING_TABLE);
        helper.setBlock(8, 1, 8, Blocks.CRAFTING_TABLE);
        StationFinder finder = new StationFinder(
                helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 3)), 9, StationType.CRAFTING_TABLE);

        helper.succeedWhen(() -> {
            helper.assertTrue(finder.step(), "поиск станции ещё идёт");
            Optional<BlockPos> found = finder.found();
            helper.assertTrue(
                    found.isPresent() && found.get().equals(helper.absolutePos(new BlockPos(3, 1, 3))),
                    "из двух верстаков обязан быть найден ближайший (3,1,3), найдено " + found);
            helper.assertTrue(
                    finder.maxSlicesPerTick() <= 4,
                    "поиск станции обязан брать не больше 4 срезов за тик, взял " + finder.maxSlicesPerTick());
            clearField(helper);
        });
    }

    @GameTest(template = FIELD, batch = "station_finder", timeoutTicks = SEARCH_TIMEOUT)
    public static void finder_TableOutsideRadius_NothingFound(GameTestHelper helper) {
        prepareField(helper);
        helper.setBlock(8, 1, 8, Blocks.CRAFTING_TABLE);
        StationFinder finder = new StationFinder(
                helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), 3, StationType.CRAFTING_TABLE);

        helper.succeedWhen(() -> {
            helper.assertTrue(finder.step(), "поиск станции ещё идёт");
            helper.assertTrue(
                    finder.found().isEmpty(),
                    "верстак за пределами радиуса 3 не должен находиться, найдено " + finder.found());
            clearField(helper);
        });
    }

    @GameTest(template = FIELD, batch = "station_finder", timeoutTicks = SEARCH_TIMEOUT)
    public static void finder_FurnaceBlockWhenLookingForTable_NothingFound(GameTestHelper helper) {
        prepareField(helper);
        helper.setBlock(3, 1, 3, Blocks.FURNACE);
        helper.setBlock(4, 1, 3, Blocks.BLAST_FURNACE);
        StationFinder finder = new StationFinder(
                helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 3)), 5, StationType.CRAFTING_TABLE);

        helper.succeedWhen(() -> {
            helper.assertTrue(finder.step(), "поиск станции ещё идёт");
            helper.assertTrue(
                    finder.found().isEmpty(),
                    "печи не верстак: поиск верстака не должен находить чужие станции, найдено " + finder.found());
            clearField(helper);
        });
    }

    @GameTest(template = FIELD, batch = "station_placer", timeoutTicks = PLACE_TIMEOUT)
    public static void placer_TableInInventory_PlacedOnSolidFloorNextToSpirit(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        prepareField(helper);
        SpiritEntity spirit = spawnSpirit(helper, new ItemStack(Items.CRAFTING_TABLE));

        Optional<BlockPos> placed = StationPlacer.place(spirit, StationType.CRAFTING_TABLE);

        helper.assertTrue(placed.isPresent(), "верстак из инвентаря обязан ставиться рядом на ровном полу");
        BlockPos cell = placed.get();
        helper.assertTrue(
                helper.getLevel().getBlockState(cell).is(Blocks.CRAFTING_TABLE),
                "в клетке " + cell + " обязан стоять верстак, стоит "
                        + helper.getLevel().getBlockState(cell));
        helper.assertTrue(
                !cell.equals(spirit.blockPosition()),
                "верстак не должен ставиться в клетку самого духа " + spirit.blockPosition());
        helper.assertTrue(
                cell.getY() == spirit.blockPosition().getY(),
                "верстак обязан стоять на уровне ног духа, а не выше или ниже: " + cell);
        helper.assertTrue(
                cell.distSqr(spirit.blockPosition())
                        <= 2 * CraftTuning.PLACE_SEARCH_RADIUS * CraftTuning.PLACE_SEARCH_RADIUS,
                "верстак обязан стоять в зоне досягаемости духа, а не дальше " + CraftTuning.PLACE_SEARCH_RADIUS
                        + " клеток: " + cell);
        int left = GameTestFixtures.countInInventory(spirit.getInventory(), Items.CRAFTING_TABLE);
        helper.assertTrue(left == 0, "поставленный верстак обязан списаться из инвентаря, осталось " + left);
        spirit.discard();
        clearField(helper);
        helper.succeed();
    }

    @GameTest(template = FIELD, batch = "station_placer", timeoutTicks = PLACE_TIMEOUT)
    public static void placer_FurnaceInInventory_FacesTheSpirit(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        prepareField(helper);
        SpiritEntity spirit = spawnSpirit(helper, new ItemStack(Items.FURNACE));

        Optional<BlockPos> placed = StationPlacer.place(spirit, StationType.FURNACE);

        helper.assertTrue(placed.isPresent(), "печь из инвентаря обязана ставиться рядом на ровном полу");
        BlockPos cell = placed.get();
        Direction facing = helper.getLevel().getBlockState(cell).getValue(HorizontalDirectionalBlock.FACING);
        double towardSpiritX = spirit.getX() - (cell.getX() + 0.5);
        double towardSpiritZ = spirit.getZ() - (cell.getZ() + 0.5);
        helper.assertTrue(
                facing.getStepX() * towardSpiritX + facing.getStepZ() * towardSpiritZ > 0,
                "печь обязана смотреть на духа (дверца к нему), а не от него: клетка " + cell + ", FACING " + facing
                        + ", дух в " + spirit.position());
        spirit.discard();
        clearField(helper);
        helper.succeed();
    }

    @GameTest(template = FIELD, batch = "station_placer", timeoutTicks = PLACE_TIMEOUT)
    public static void placer_NoFreeCellAround_ReturnsEmptyAndKeepsItem(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        prepareField(helper);
        SpiritEntity spirit = spawnSpirit(helper, new ItemStack(Items.CRAFTING_TABLE));
        fillRing(helper, Blocks.STONE);

        Optional<BlockPos> placed = StationPlacer.place(spirit, StationType.CRAFTING_TABLE);

        helper.assertTrue(placed.isEmpty(), "вокруг духа нет свободных клеток: ставить негде, поставлено в " + placed);
        int left = GameTestFixtures.countInInventory(spirit.getInventory(), Items.CRAFTING_TABLE);
        helper.assertTrue(left == 1, "при отказе верстак обязан остаться в инвентаре, осталось " + left);
        spirit.discard();
        clearField(helper);
        helper.succeed();
    }

    @GameTest(template = FIELD, batch = "station_placer", timeoutTicks = PLACE_TIMEOUT)
    public static void placer_NoFloorAround_ReturnsEmpty(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        prepareField(helper);
        SpiritEntity spirit = spawnSpirit(helper, new ItemStack(Items.CRAFTING_TABLE));
        for (int dx = -CraftTuning.PLACE_SEARCH_RADIUS; dx <= CraftTuning.PLACE_SEARCH_RADIUS; dx++) {
            for (int dz = -CraftTuning.PLACE_SEARCH_RADIUS; dz <= CraftTuning.PLACE_SEARCH_RADIUS; dz++) {
                if (dx != 0 || dz != 0) {
                    helper.setBlock(SPIRIT_CELL.getX() + dx, 0, SPIRIT_CELL.getZ() + dz, Blocks.AIR);
                }
            }
        }

        Optional<BlockPos> placed = StationPlacer.place(spirit, StationType.CRAFTING_TABLE);

        helper.assertTrue(
                placed.isEmpty(),
                "без твёрдого пола под клеткой верстак не ставится (он повис бы в воздухе), поставлено в " + placed);
        spirit.discard();
        clearField(helper);
        helper.succeed();
    }

    @GameTest(template = FIELD, batch = "station_placer", timeoutTicks = PLACE_TIMEOUT)
    public static void placer_NoItemInInventory_ReturnsEmptyAndPlacesNothing(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        prepareField(helper);
        SpiritEntity spirit = spawnSpirit(helper, ItemStack.EMPTY);

        Optional<BlockPos> placed = StationPlacer.place(spirit, StationType.CRAFTING_TABLE);

        helper.assertTrue(placed.isEmpty(), "без верстака в инвентаре ставить нечего, поставлено в " + placed);
        helper.assertTrue(
                !hasTableAround(helper), "станция не должна появляться из воздуха, когда предмета в инвентаре нет");
        spirit.discard();
        clearField(helper);
        helper.succeed();
    }

    private static boolean hasTableAround(GameTestHelper helper) {
        for (int x = 0; x < GameTestFixtures.FLOOR_SIZE; x++) {
            for (int z = 0; z < GameTestFixtures.FLOOR_SIZE; z++) {
                if (helper.getBlockState(new BlockPos(x, 1, z)).is(Blocks.CRAFTING_TABLE)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void fillRing(GameTestHelper helper, Block block) {
        for (int dx = -CraftTuning.PLACE_SEARCH_RADIUS; dx <= CraftTuning.PLACE_SEARCH_RADIUS; dx++) {
            for (int dz = -CraftTuning.PLACE_SEARCH_RADIUS; dz <= CraftTuning.PLACE_SEARCH_RADIUS; dz++) {
                if (dx != 0 || dz != 0) {
                    helper.setBlock(SPIRIT_CELL.getX() + dx, 1, SPIRIT_CELL.getZ() + dz, block);
                }
            }
        }
    }

    private static SpiritEntity spawnSpirit(GameTestHelper helper, ItemStack stack) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_CELL);
        if (!stack.isEmpty()) {
            spirit.getInventory().addItem(stack);
        }
        return spirit;
    }

    private static void prepareField(GameTestHelper helper) {
        clearField(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
    }

    private static void clearField(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
    }
}
