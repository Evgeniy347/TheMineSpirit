package com.theminespirit.gametest;

import static com.theminespirit.gametest.PerceptionTestSupport.TEST_TIMEOUT_TICKS;
import static com.theminespirit.gametest.PerceptionTestSupport.assertSucceeded;
import static com.theminespirit.gametest.PerceptionTestSupport.coordOf;
import static com.theminespirit.gametest.PerceptionTestSupport.launch;
import static com.theminespirit.gametest.PerceptionTestSupport.spawnSpirit;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockBox;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.observation.ScanRadius;
import com.theminespirit.core.observation.ScanTuning;
import com.theminespirit.core.observation.SectionCursor;
import com.theminespirit.core.perception.Finding;
import com.theminespirit.core.perception.LocateQuery;
import com.theminespirit.core.perception.Sighting;
import com.theminespirit.neoforge.LocateScript;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PerceptionLocateGameTests {
    private static final int LOCATE_RADIUS = 8;
    private static final int POSITIONS_PER_SLICE = 4096;

    private PerceptionLocateGameTests() {
    }

    @GameTest(template = "arena", batch = "perception_locate_block", timeoutTicks = TEST_TIMEOUT_TICKS)
    public static void locateBlock_OreFiveBlocksAway_ReportsNearestMatchingBlock(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(6, 1, 4, Blocks.DIAMOND_ORE);
        helper.setBlock(8, 1, 8, Blocks.DIAMOND_ORE);
        helper.setBlock(2, 1, 4, Blocks.EMERALD_ORE);
        SpiritEntity spirit = spawnSpirit(helper, 1, 1, 4);
        SpiritRun run = launch(helper, spirit, "locate_block",
                Map.of("filter", List.of("minecraft:diamond_ore"), "radius", LOCATE_RADIUS));

        helper.succeedWhen(() -> {
            assertSucceeded(helper, spirit, run, "locate_block");
            Finding finding = spirit.finding().orElse(null);
            Sighting expected = new Sighting("block", "minecraft:diamond_ore", coordOf(helper, 6, 1, 4),
                    "расстояние 5.0");
            helper.assertTrue(new Finding("locate_block", List.of(expected)).equals(finding),
                    "locate_block с фильтром diamond_ore обязан назвать ближайшую алмазную руду в пяти блоках "
                            + expected + ", а не изумрудную рядом и не дальнюю алмазную: " + finding);
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "arena", batch = "perception_locate_tree", timeoutTicks = TEST_TIMEOUT_TICKS)
    public static void locateTree_OakSixBlocksAway_ReportsOakTrunkNotNearerBirch(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        for (int y = 1; y <= 4; y++) {
            helper.setBlock(3, y, 4, Blocks.BIRCH_LOG);
        }
        for (int y = 1; y <= 4; y++) {
            helper.setBlock(7, y, 4, Blocks.OAK_LOG);
        }
        SpiritEntity spirit = spawnSpirit(helper, 1, 1, 4);
        SpiritRun run = launch(helper, spirit, "locate_tree", Map.of("species", "oak", "radius", LOCATE_RADIUS));

        helper.succeedWhen(() -> {
            assertSucceeded(helper, spirit, run, "locate_tree");
            Finding finding = spirit.finding().orElse(null);
            Sighting expected = new Sighting("tree", "minecraft:oak_log", coordOf(helper, 7, 1, 4), "расстояние 6.0");
            helper.assertTrue(new Finding("locate_tree", List.of(expected)).equals(finding),
                    "locate_tree с породой oak обязан назвать основание дуба в шести блоках " + expected
                            + ", а не берёзу в двух блоках: " + finding);
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "arena", batch = "perception_locate_container", timeoutTicks = TEST_TIMEOUT_TICKS)
    public static void locateContainer_ChestFourBlocksAway_ReportsNearestContainer(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(5, 1, 4, Blocks.CHEST);
        helper.setBlock(8, 1, 8, Blocks.BARREL);
        SpiritEntity spirit = spawnSpirit(helper, 1, 1, 4);
        SpiritRun run = launch(helper, spirit, "locate_container", Map.of("radius", LOCATE_RADIUS));

        helper.succeedWhen(() -> {
            assertSucceeded(helper, spirit, run, "locate_container");
            Finding finding = spirit.finding().orElse(null);
            Sighting expected = new Sighting("container", "minecraft:chest", coordOf(helper, 5, 1, 4),
                    "расстояние 4.0");
            helper.assertTrue(new Finding("locate_container", List.of(expected)).equals(finding),
                    "locate_container обязан назвать ближайший сундук в четырёх блоках " + expected
                            + ", а не бочку подальше: " + finding);
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "arena", batch = "perception_locate_none", timeoutTicks = TEST_TIMEOUT_TICKS)
    public static void locateBlock_NothingMatches_ReportsNotFound(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = spawnSpirit(helper, 4, 1, 4);
        SpiritRun run = launch(helper, spirit, "locate_block",
                Map.of("filter", List.of("minecraft:diamond_ore"), "radius", LOCATE_RADIUS));

        helper.succeedWhen(() -> {
            assertSucceeded(helper, spirit, run, "locate_block");
            Finding finding = spirit.finding().orElse(null);
            helper.assertTrue(finding != null && finding.script().equals("locate_block") && finding.isEmpty(),
                    "поиск несуществующей руды обязан завершиться успехом с пустой находкой («не найдено»), а не "
                            + "ошибкой и не выдуманной позицией: " + finding);
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "arena", batch = "perception_locate_portions", timeoutTicks = TEST_TIMEOUT_TICKS)
    public static void locateScript_Radius32_ReadsFourSlicesPerTick(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = spawnSpirit(helper, 4, 1, 4);
        BlockCoord center = new BlockCoord(spirit.getBlockX(), spirit.getBlockY(), spirit.getBlockZ());
        BlockBox cube = BlockBox.cube(center, ScanRadius.MAX)
                .clampY(helper.getLevel().getMinBuildHeight(), helper.getLevel().getMaxBuildHeight() - 1)
                .orElseThrow();
        int totalSlices = SectionCursor.of(cube, center).total();
        LocateScript script = new LocateScript(spirit,
                LocateQuery.block(IdFilter.of(List.of("minecraft:stone")), ScanRadius.of(ScanRadius.MAX)));

        int ticks = 1;
        while (script.tick() == ScriptStatus.RUNNING) {
            ticks++;
            helper.assertTrue(ticks < 400, "locate_block радиуса 32 обязан закончиться за конечное число порций");
        }

        helper.assertTrue(ticks == (totalSlices + ScanTuning.SLICES_PER_TICK - 1) / ScanTuning.SLICES_PER_TICK,
                "куб из " + totalSlices + " срезов обязан читаться порциями по " + ScanTuning.SLICES_PER_TICK
                        + " за тик, тиков потребовалось " + ticks + ": один тик значит, что поиск читает весь куб разом");
        helper.assertTrue(script.examinedPositions() > 0,
                "тест не доказывает порции: пол из камня обязан быть прочитан, прочитано " + script.examinedPositions());
        helper.assertTrue(script.maxExaminedPositionsPerTick() <= ScanTuning.SLICES_PER_TICK * POSITIONS_PER_SLICE,
                "за тик поиск читает не больше " + ScanTuning.SLICES_PER_TICK + " срезов по " + POSITIONS_PER_SLICE
                        + " позиций, а прочитал " + script.maxExaminedPositionsPerTick());
        GameTestFixtures.reset(helper);
        helper.succeed();
    }
}
