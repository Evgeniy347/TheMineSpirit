package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.neoforge.BlockLocator;
import com.theminespirit.neoforge.BlockSets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BlockLocatorGameTests {
    private static final int SKY_HEIGHT = 40;
    private static final int SEARCH_RADIUS = 8;
    private static final int POSITIONS_PER_SECTION = 4096;
    private static final int SLICES_PER_STEP = 4;

    private BlockLocatorGameTests() {
    }

    @GameTest(template = "empty", batch = "locator_air", timeoutTicks = 40)
    public static void blockLocator_AirOnlyArea_ReadsNoPositions(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(1, SKY_HEIGHT, 1));
        BlockLocator.Search search = BlockLocator.search(helper.getLevel(), center, SEARCH_RADIUS,
                Set.of(Blocks.DIAMOND_ORE));

        List<BlockPos> found = drain(search);

        helper.assertTrue(found.isEmpty(), "в воздухе не должно найтись алмазной руды: найдено " + found);
        helper.assertTrue(search.examinedPositions() == 0,
                "секции из одного воздуха обязаны пропускаться целиком, а прочитано " + search.examinedPositions()
                        + " позиций: поиск по пустоте иначе стоит дороже скана");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "locator_single", timeoutTicks = 40)
    public static void blockLocator_SingleTargetBlock_ReadsAtMostOneSectionAndFindsIt(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(1, SKY_HEIGHT, 1));
        helper.getLevel().setBlock(center, Blocks.DIAMOND_ORE.defaultBlockState(), 2);
        try {
            BlockLocator.Search search = BlockLocator.search(helper.getLevel(), center, SEARCH_RADIUS,
                    Set.of(Blocks.DIAMOND_ORE));

            List<BlockPos> found = drain(search);

            helper.assertTrue(found.equals(List.of(center)),
                    "поиск обязан найти единственный блок в " + center + ", найдено " + found);
            helper.assertTrue(search.examinedPositions() <= POSITIONS_PER_SECTION,
                    "блок лежит в одной секции: читать допустимо не более " + POSITIONS_PER_SECTION
                            + " позиций, прочитано " + search.examinedPositions());
            helper.assertTrue(search.examinedPositions() > 0,
                    "секцию с искомым блоком обязано прочитать хоть частично, иначе блок найден случайно");
        } finally {
            helper.getLevel().setBlock(center, Blocks.AIR.defaultBlockState(), 2);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "locator_palette", timeoutTicks = 40)
    public static void blockLocator_SectionWithoutTargetBlock_IsSkippedByPalette(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        BlockPos center = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockLocator.Search search = BlockLocator.search(helper.getLevel(), center, SEARCH_RADIUS,
                Set.of(Blocks.DIAMOND_ORE));

        List<BlockPos> found = drain(search);

        helper.assertTrue(found.isEmpty(), "в каменном полу нет алмазной руды: найдено " + found);
        helper.assertTrue(search.examinedPositions() == 0,
                "секция с камнем, но без искомого блока, обязана пропускаться по палитре, а прочитано "
                        + search.examinedPositions() + " позиций");
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "locator_order", timeoutTicks = 40)
    public static void blockLocator_TwoTargetsAtDifferentDistances_ReportsNearerSliceFirst(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        BlockPos center = helper.absolutePos(new BlockPos(1, SKY_HEIGHT, 1));
        BlockPos near = center.offset(1, 0, 0);
        BlockPos far = center.offset(SEARCH_RADIUS, 0, SEARCH_RADIUS);
        helper.getLevel().setBlock(near, Blocks.GOLD_ORE.defaultBlockState(), 2);
        helper.getLevel().setBlock(far, Blocks.GOLD_ORE.defaultBlockState(), 2);
        try {
            BlockLocator.Search search = BlockLocator.search(helper.getLevel(), center, SEARCH_RADIUS,
                    Set.of(Blocks.GOLD_ORE));

            List<BlockPos> found = drain(search);

            helper.assertTrue(found.contains(near) && found.contains(far) && found.size() == 2,
                    "оба блока в радиусе обязаны быть найдены, найдено " + found);
            helper.assertTrue(found.indexOf(near) < found.indexOf(far),
                    "ближний блок обязан идти раньше дальнего: " + found);
        } finally {
            helper.getLevel().setBlock(near, Blocks.AIR.defaultBlockState(), 2);
            helper.getLevel().setBlock(far, Blocks.AIR.defaultBlockState(), 2);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "locator_sets", timeoutTicks = 40)
    public static void blockSets_OreAndLogFilters_ResolveRegistryBlocks(GameTestHelper helper) {
        Set<Block> ores = BlockSets.resolve(IdFilter.of(List.of("*_ore")));
        Set<Block> exact = BlockSets.resolve(IdFilter.of(List.of("minecraft:oak_log")));

        helper.assertTrue(ores.contains(Blocks.IRON_ORE) && ores.contains(Blocks.DEEPSLATE_COAL_ORE)
                        && !ores.contains(Blocks.STONE),
                "фильтр *_ore обязан включать обычные и глубинные руды и не включать камень");
        helper.assertTrue(exact.equals(Set.of(Blocks.OAK_LOG)),
                "точный id обязан давать ровно один блок, получено " + exact);
        helper.succeed();
    }

    private static List<BlockPos> drain(BlockLocator.Search search) {
        List<BlockPos> found = new ArrayList<>();
        while (!search.done()) {
            found.addAll(search.step(SLICES_PER_STEP));
        }
        return found;
    }
}
