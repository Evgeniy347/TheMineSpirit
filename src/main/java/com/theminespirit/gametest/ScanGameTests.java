package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockBox;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.observation.ScanRadius;
import com.theminespirit.core.observation.ScanTuning;
import com.theminespirit.core.observation.SectionCursor;
import com.theminespirit.core.observation.SectionSlice;
import com.theminespirit.core.observation.WorldObservation;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.WorldScanner;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ScanGameTests {
    private static final int IRON_ORES = 3;
    private static final int COAL_ORES = 20;
    private static final int READS_PER_TICK_BUDGET = 16_384;
    private static final int SLAB_REACH = 40;

    private ScanGameTests() {
    }

    @GameTest(template = "empty", batch = "scan_filter", timeoutTicks = 60)
    public static void scanScript_OreFloorWithOreFilter_ReportsOnlyOres(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        buildOreFloor(helper);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        spirit.startTask(new ScriptCall("scan", Map.of("radius", 8, "filter", List.of("*_ore"))));

        helper.succeedWhen(() -> {
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "scan обязан завершиться успехом за 60 тиков, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            WorldObservation observation = spirit.observation().orElse(null);
            helper.assertTrue(observation != null,
                    "после успешного scan наблюдение обязано лежать в духе: иначе результат скана некуда читать");
            helper.assertTrue(GameTestFixtures.blockCount(observation, "minecraft:iron_ore") == IRON_ORES,
                    "в наблюдении должно быть " + IRON_ORES + " блока iron_ore, фактически "
                            + GameTestFixtures.blockCount(observation, "minecraft:iron_ore"));
            helper.assertTrue(GameTestFixtures.blockCount(observation, "minecraft:coal_ore") == COAL_ORES,
                    "в наблюдении должно быть " + COAL_ORES + " блоков coal_ore, фактически "
                            + GameTestFixtures.blockCount(observation, "minecraft:coal_ore"));
            helper.assertTrue(GameTestFixtures.blockCount(observation, "minecraft:stone") == 0,
                    "фильтр *_ore обязан отсекать камень: иначе filter не уменьшает наблюдение для ИИ, "
                            + "фактически камня " + GameTestFixtures.blockCount(observation, "minecraft:stone"));
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "empty", batch = "scan_nofilter", timeoutTicks = 60)
    public static void scanScript_OreFloorWithoutFilter_ReportsStoneToo(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        buildOreFloor(helper);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        spirit.startTask(new ScriptCall("scan", Map.of("radius", 8)));

        helper.succeedWhen(() -> {
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "scan без фильтра обязан завершиться успехом за 60 тиков, причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            WorldObservation observation = spirit.observation().orElse(null);
            helper.assertTrue(observation != null, "наблюдение после scan обязано лежать в духе");
            int stone = GameTestFixtures.blockCount(observation, "minecraft:stone");
            helper.assertTrue(stone >= GameTestFixtures.FLOOR_SIZE * GameTestFixtures.FLOOR_SIZE - IRON_ORES - COAL_ORES,
                    "scan без фильтра обязан видеть весь камень пола (не меньше 58 блоков), фактически " + stone);
            helper.assertTrue(GameTestFixtures.blockCount(observation, "minecraft:iron_ore") == IRON_ORES,
                    "scan без фильтра обязан видеть и руду: iron_ore ожидалось " + IRON_ORES);
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "empty", batch = "scan_cost", timeoutTicks = 100)
    public static void scanSession_Radius32OverSlab_ReadsWithinBudgetPerTickAndSkipsEmptySections(
            GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        buildSlab(helper);
        BlockPos center = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockCoord centerCoord = new BlockCoord(center.getX(), center.getY(), center.getZ());
        WorldScanner.Session session = new WorldScanner.Session(helper.getLevel(), "scan_cost", centerCoord,
                ScanRadius.of(32), IdFilter.matchAll(), new SimpleContainer(1));

        int steps = 0;
        int mostReadsInOneTick = 0;
        while (!session.done()) {
            int before = session.examinedPositions();
            session.step(ScanTuning.SLICES_PER_TICK);
            mostReadsInOneTick = Math.max(mostReadsInOneTick, session.examinedPositions() - before);
            steps++;
        }

        helper.assertTrue(mostReadsInOneTick <= READS_PER_TICK_BUDGET,
                "за один тик скан прочитал " + mostReadsInOneTick + " позиций при бюджете " + READS_PER_TICK_BUDGET
                        + ": порция в " + ScanTuning.SLICES_PER_TICK + " срезов не держит серверный тик");
        helper.assertTrue(mostReadsInOneTick > 0,
                "тест не доказывает бюджет: ни один тик не читал блоков, плита пола не попала в скан");
        long nonEmptyVolume = volumeOfNonEmptySlices(helper, centerCoord, 32);
        helper.assertTrue(session.examinedPositions() == nonEmptyVolume,
                "скан обязан читать позиции только непустых секций: прочитано " + session.examinedPositions()
                        + ", в непустых секциях " + nonEmptyVolume);
        long cubeVolume = 65L * 65L * 65L;
        helper.assertTrue(session.examinedPositions() < cubeVolume / 2,
                "пустые секции обязаны пропускаться: прочитано " + session.examinedPositions() + " из " + cubeVolume);
        helper.assertTrue(steps <= (125 + ScanTuning.SLICES_PER_TICK - 1) / ScanTuning.SLICES_PER_TICK,
                "скан радиуса 32 обязан закончиться не более чем за 32 порции, а потребовалось " + steps);
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "scan_long", timeoutTicks = 80)
    public static void scanScript_Radius32InMostlyEmptyWorld_FinishesWithinTickBudget(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        long startTick = helper.getTick();
        spirit.startTask(new ScriptCall("scan", Map.of("radius", 32)));

        helper.succeedWhen(() -> {
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "scan радиуса 32 обязан завершиться за 80 тиков, причина: " + spirit.taskFailureReason().orElse("нет"));
            long took = helper.getTick() - startTick;
            helper.assertTrue(took <= 40,
                    "scan радиуса 32 обязан уложиться в ~32 тика порциями, а занял " + took
                            + ": иначе он читает слишком мало срезов за тик");
            helper.assertTrue(took >= 2,
                    "scan радиуса 32 обязан растягиваться на несколько тиков, а закончился за " + took
                            + ": весь куб прочитан одним тиком");
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "empty", batch = "scan_unloaded", timeoutTicks = 100)
    public static void scanSession_Radius32NearUnloadedChunks_DoesNotLoadChunks(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        BlockPos base = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos unloaded = GameTestFixtures.firstUnloadedChunkEast(helper, base);
        helper.assertTrue(unloaded != null,
                "в пределах 256 чанков к востоку обязана найтись незагруженная область: иначе тест не проверяет пропуск");
        BlockCoord center = new BlockCoord(unloaded.getX() - 24, base.getY(), base.getZ());
        int before = helper.getLevel().getChunkSource().getLoadedChunksCount();

        WorldScanner.Session session = new WorldScanner.Session(helper.getLevel(), "scan_unloaded", center,
                ScanRadius.of(32), IdFilter.of(List.of("*_ore")), new SimpleContainer(1));
        while (!session.done()) {
            session.step(ScanTuning.SLICES_PER_TICK);
        }
        WorldScanner.Scan scan = session.result();
        int after = helper.getLevel().getChunkSource().getLoadedChunksCount();

        helper.assertTrue(scan.skippedChunks() > 0,
                "скан у края загруженной области обязан встретить незагруженные чанки, пропущено " + scan.skippedChunks());
        helper.assertTrue(after == before,
                "скан порциями не должен грузить чанки: было " + before + ", стало " + after);
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "scan_cancel", timeoutTicks = 60)
    public static void scanScript_CancelledOnSecondTick_KeepsPreviousObservation(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        buildOreFloor(helper);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        spirit.startTask(new ScriptCall("scan", Map.of("radius", 2)));

        helper.runAfterDelay(10, () -> {
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "первый scan обязан успеть завершиться за 10 тиков, причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            WorldObservation first = spirit.observation().orElse(null);
            helper.assertTrue(first != null && first.radius() == 2, "первый scan обязан положить наблюдение радиуса 2");
            spirit.startTask(new ScriptCall("scan", Map.of("radius", 32)));
            helper.runAfterDelay(2, () -> {
                spirit.cancelTask();
                helper.runAfterDelay(2, () -> {
                    helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.CANCELLED,
                            "отменённый scan обязан иметь статус CANCELLED, фактически "
                                    + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
                    helper.assertTrue(spirit.observation().orElse(null) == first,
                            "отменённый scan не должен писать частичное наблюдение: прежнее наблюдение обязано "
                                    + "остаться тем же объектом");
                    GameTestFixtures.reset(helper);
                    helper.succeed();
                });
            });
        });
    }

    private static void buildOreFloor(GameTestHelper helper) {
        int index = 0;
        for (int x = 0; x < GameTestFixtures.FLOOR_SIZE; x++) {
            for (int z = 0; z < GameTestFixtures.FLOOR_SIZE; z++) {
                if (index < IRON_ORES) {
                    helper.setBlock(x, 0, z, Blocks.IRON_ORE);
                } else if (index < IRON_ORES + COAL_ORES) {
                    helper.setBlock(x, 0, z, Blocks.COAL_ORE);
                } else {
                    helper.setBlock(x, 0, z, Blocks.STONE);
                }
                index++;
            }
        }
    }

    private static void buildSlab(GameTestHelper helper) {
        for (int x = -SLAB_REACH; x <= SLAB_REACH; x++) {
            for (int z = -SLAB_REACH; z <= SLAB_REACH; z++) {
                BlockPos absolute = helper.absolutePos(new BlockPos(x, 0, z));
                if (helper.getLevel().hasChunk(absolute.getX() >> 4, absolute.getZ() >> 4)) {
                    helper.getLevel().setBlock(absolute, Blocks.STONE.defaultBlockState(), 2);
                }
            }
        }
    }

    private static long volumeOfNonEmptySlices(GameTestHelper helper, BlockCoord center, int radius) {
        BlockBox cube = BlockBox.cube(center, radius).clampY(helper.getLevel().getMinBuildHeight(),
                helper.getLevel().getMaxBuildHeight() - 1).orElseThrow();
        SectionCursor cursor = SectionCursor.of(cube, center);
        long volume = 0;
        while (!cursor.isDone()) {
            for (SectionSlice slice : cursor.next(ScanTuning.SLICES_PER_TICK)) {
                if (helper.getLevel().getChunk(slice.sectionX(), slice.sectionZ(), ChunkStatus.FULL, false)
                        instanceof LevelChunk chunk
                        && !chunk.getSection(chunk.getSectionIndexFromSectionY(slice.sectionY())).hasOnlyAir()) {
                    volume += slice.volume();
                }
            }
        }
        return volume;
    }
}
