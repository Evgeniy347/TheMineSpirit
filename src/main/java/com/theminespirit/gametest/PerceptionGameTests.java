package com.theminespirit.gametest;

import static com.theminespirit.gametest.PerceptionTestSupport.TEST_TIMEOUT_TICKS;
import static com.theminespirit.gametest.PerceptionTestSupport.assertSucceeded;
import static com.theminespirit.gametest.PerceptionTestSupport.launch;
import static com.theminespirit.gametest.PerceptionTestSupport.spawnSpirit;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockBox;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.EntityRef;
import com.theminespirit.core.observation.ItemCount;
import com.theminespirit.core.observation.ScanRadius;
import com.theminespirit.core.observation.ScanTuning;
import com.theminespirit.core.observation.SectionCursor;
import com.theminespirit.core.perception.HazardRef;
import com.theminespirit.core.perception.HazardType;
import com.theminespirit.core.perception.PerceptionResult;
import com.theminespirit.core.perception.PerceptionType;
import com.theminespirit.core.script.PerceptionParams;
import com.theminespirit.neoforge.HazardScript;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.ScanScript;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PerceptionGameTests {
    private static final int COAL_ORES = 5;
    private static final int IRON_ORES = 2;
    private static final int COWS = 3;
    private static final int PLATFORM_LEVEL = 4;
    private static final int PLATFORM_LAST_X = 6;
    private static final double LAVA_DISTANCE = Math.sqrt(4 * 4 + 1);
    private static final double DROP_DISTANCE = 3.0;
    private static final double EPSILON = 1e-6;

    private PerceptionGameTests() {}

    @GameTest(template = "arena", batch = "perception_scan_blocks", timeoutTicks = TEST_TIMEOUT_TICKS)
    public static void scanBlocks_OreFloorWithOreFilter_ReportsOnlyOres(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        for (int x = 1; x <= COAL_ORES; x++) {
            helper.setBlock(x, 0, 1, Blocks.COAL_ORE);
        }
        helper.setBlock(6, 0, 6, Blocks.IRON_ORE);
        helper.setBlock(7, 0, 6, Blocks.IRON_ORE);
        SpiritEntity spirit = spawnSpirit(helper, 4, 1, 4);
        SpiritRun run = launch(helper, spirit, "scan_blocks", Map.of("radius", 8, "filter", List.of("*_ore")));

        helper.succeedWhen(() -> {
            assertSucceeded(helper, spirit, run, "scan_blocks");
            PerceptionResult result = spirit.perceptionResult().orElse(null);
            helper.assertTrue(
                    result != null && result.type() == PerceptionType.BLOCKS,
                    "после scan_blocks результат восприятия обязан лежать в духе с типом BLOCKS: иначе ИИ нечего читать");
            helper.assertTrue(
                    result.blocks().size() == 2,
                    "в результате фильтра *_ore обязаны быть ровно два вида руды (уголь и железо), а там: "
                            + result.blocks() + ". Лишние виды значат, что фильтр не применяется");
            helper.assertTrue(
                    result.blocks().stream().allMatch(entry -> entry.item().endsWith("_ore")),
                    "в результате должны быть только руды, а там: " + result.blocks());
            helper.assertTrue(
                    countOf(result, "minecraft:coal_ore") == COAL_ORES
                            && countOf(result, "minecraft:iron_ore") == IRON_ORES,
                    "руды в результате обязаны считаться точно: угля " + COAL_ORES + ", железа " + IRON_ORES
                            + ", фактически " + result.blocks());
            helper.assertTrue(
                    result.entities().isEmpty(),
                    "scan_blocks не собирает сущности, а в результате: " + result.entities());
            helper.assertTrue(
                    spirit.observation().isEmpty(),
                    "scan_blocks кладёт результат восприятия, а не полное наблюдение: полный скан остался за scan");
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "perception_scan_entities", timeoutTicks = TEST_TIMEOUT_TICKS)
    public static void scanEntities_CowsAndSheep_ReportsOnlyCows(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        frozen(helper.spawn(EntityType.COW, new BlockPos(2, 1, 2)));
        frozen(helper.spawn(EntityType.COW, new BlockPos(6, 1, 2)));
        frozen(helper.spawn(EntityType.COW, new BlockPos(2, 1, 6)));
        frozen(helper.spawn(EntityType.SHEEP, new BlockPos(6, 1, 6)));
        frozen(helper.spawn(EntityType.SHEEP, new BlockPos(4, 1, 2)));
        SpiritEntity spirit = spawnSpirit(helper, 4, 1, 4);
        SpiritRun run =
                launch(helper, spirit, "scan_entities", Map.of("radius", 8, "filter", List.of("minecraft:cow")));

        helper.succeedWhen(() -> {
            assertSucceeded(helper, spirit, run, "scan_entities");
            PerceptionResult result = spirit.perceptionResult().orElse(null);
            helper.assertTrue(
                    result != null && result.type() == PerceptionType.ENTITIES,
                    "после scan_entities результат восприятия обязан лежать в духе с типом ENTITIES");
            helper.assertTrue(
                    result.entities().size() == COWS,
                    "в результате фильтра minecraft:cow обязаны быть ровно " + COWS + " коровы, а там: "
                            + result.entities().stream().map(EntityRef::type).toList()
                            + ". Овцы или дух в результате значат, что фильтр не применяется к сущностям");
            helper.assertTrue(
                    result.entities().stream().allMatch(entity -> entity.type().equals("minecraft:cow")),
                    "в результате должны быть только коровы, а там: "
                            + result.entities().stream().map(EntityRef::type).toList());
            helper.assertTrue(
                    result.blocks().isEmpty(), "scan_entities не собирает блоки, а в результате: " + result.blocks());
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "perception_scan_cost", timeoutTicks = TEST_TIMEOUT_TICKS)
    public static void scanScript_BlocksAndEntitiesFocus_ReadOnlyTheirOwnPartOfWorld(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        frozen(helper.spawn(EntityType.COW, new BlockPos(2, 1, 2)));
        SpiritEntity spirit = spawnSpirit(helper, 4, 1, 4);
        ScanScript blocks = new ScanScript(
                spirit, PerceptionParams.blocks(Map.of("radius", 8, "filter", List.of("minecraft:stone"))));
        ScanScript entities = new ScanScript(spirit, PerceptionParams.entities(Map.of("radius", 8)));

        while (blocks.tick() == ScriptStatus.RUNNING) {
            helper.assertTrue(blocks.stepCalls() < 200, "scan_blocks радиуса 8 обязан закончиться за считанные порции");
        }
        while (entities.tick() == ScriptStatus.RUNNING) {
            helper.assertTrue(entities.stepCalls() < 200, "scan_entities обязан закончиться сразу");
        }

        helper.assertTrue(
                blocks.examinedPositions() > 0,
                "scan_blocks по полу из камня обязан читать позиции блоков: прочитано " + blocks.examinedPositions());
        helper.assertTrue(
                blocks.entityQueries() == 0,
                "scan_blocks не вправе опрашивать сущности: запросов " + blocks.entityQueries()
                        + ", иначе «только блоки» стоит как полный скан");
        helper.assertTrue(
                entities.examinedPositions() == 0,
                "scan_entities не вправе читать блоки: прочитано позиций " + entities.examinedPositions()
                        + ", иначе «только сущности» стоит как полный скан");
        helper.assertTrue(
                entities.entityQueries() == 1,
                "scan_entities опрашивает сущности ровно один раз за скан: запросов " + entities.entityQueries());
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "perception_hazard_lava", timeoutTicks = TEST_TIMEOUT_TICKS)
    public static void senseHazard_LavaFourBlocksAway_ReportsLavaAndLeavesSpiritInPlace(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(8, 0, 4, Blocks.LAVA);
        SpiritEntity spirit = spawnSpirit(helper, 4, 1, 4);
        BlockPos before = spirit.blockPosition();
        BlockPos lava = helper.absolutePos(new BlockPos(8, 0, 4));
        SpiritRun run = launch(helper, spirit, "sense_hazard", Map.of("radius", 8));

        helper.succeedWhen(() -> {
            assertSucceeded(helper, spirit, run, "sense_hazard");
            PerceptionResult result = spirit.perceptionResult().orElse(null);
            helper.assertTrue(
                    result != null && result.type() == PerceptionType.HAZARDS,
                    "после sense_hazard результат восприятия обязан лежать в духе с типом HAZARDS");
            OptionalDouble nearestLava = nearestDistance(result, HazardType.LAVA);
            helper.assertTrue(
                    nearestLava.isPresent(),
                    "лава в четырёх блоках обязана попасть в факты об опасностях, а там: " + result.hazards());
            helper.assertTrue(
                    Math.abs(nearestLava.getAsDouble() - LAVA_DISTANCE) < EPSILON,
                    "расстояние до лавы обязано быть " + LAVA_DISTANCE + " (4 по горизонтали, 1 по вертикали), а там "
                            + nearestLava.getAsDouble());
            helper.assertTrue(
                    result.hazards().stream()
                            .anyMatch(hazard -> hazard.type() == HazardType.LAVA
                                    && hazard.pos().equals(new BlockCoord(lava.getX(), lava.getY(), lava.getZ()))),
                    "опасность обязана называть позицию лавы " + lava + ", а там: " + result.hazards());
            helper.assertTrue(
                    nearestDistance(result, HazardType.WATER).isEmpty(),
                    "воды на площадке нет, а в фактах: " + result.hazards());
            helper.assertTrue(
                    spirit.blockPosition().equals(before),
                    "sense_hazard отдаёт факты и не двигает духа: было " + before + ", стало " + spirit.blockPosition()
                            + ". Рефлексы остаются за политиками");
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "perception_hazard_drop", timeoutTicks = TEST_TIMEOUT_TICKS)
    public static void senseHazard_PlatformEdgeThreeBlocksAway_ReportsDropAndLeavesSpiritInPlace(
            GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        for (int x = 0; x <= PLATFORM_LAST_X; x++) {
            for (int z = 0; z < GameTestFixtures.FLOOR_SIZE; z++) {
                helper.setBlock(x, PLATFORM_LEVEL, z, Blocks.STONE);
            }
        }
        SpiritEntity spirit = spawnSpirit(helper, 3, PLATFORM_LEVEL + 1, 4);
        BlockPos before = spirit.blockPosition();
        SpiritRun run = launch(helper, spirit, "sense_hazard", Map.of("radius", 8));

        helper.succeedWhen(() -> {
            assertSucceeded(helper, spirit, run, "sense_hazard");
            PerceptionResult result = spirit.perceptionResult().orElse(null);
            helper.assertTrue(
                    result != null && result.type() == PerceptionType.HAZARDS,
                    "после sense_hazard результат восприятия обязан лежать в духе с типом HAZARDS");
            OptionalDouble nearestDrop = nearestDistance(result, HazardType.DROP);
            helper.assertTrue(
                    nearestDrop.isPresent(),
                    "край площадки с перепадом в " + 3 + " блока обязан попасть в факты как DROP, а там: "
                            + result.hazards());
            helper.assertTrue(
                    Math.abs(nearestDrop.getAsDouble() - DROP_DISTANCE) < EPSILON,
                    "ближайший обрыв в трёх блоках от духа (до края 3), а там " + nearestDrop.getAsDouble()
                            + ": середина площадки обрывом не является");
            helper.assertTrue(
                    nearestDistance(result, HazardType.LAVA).isEmpty()
                            && nearestDistance(result, HazardType.WATER).isEmpty(),
                    "лавы и воды на площадке нет, а в фактах: " + result.hazards());
            helper.assertTrue(
                    spirit.blockPosition().equals(before),
                    "sense_hazard отдаёт факты и не двигает духа: было " + before + ", стало "
                            + spirit.blockPosition());
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "perception_hazard_cost", timeoutTicks = TEST_TIMEOUT_TICKS)
    public static void senseHazard_Radius32NearUnloadedChunks_ReadsFourSlicesPerTickAndLoadsNoChunks(
            GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        BlockPos base = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos unloaded = GameTestFixtures.firstUnloadedChunkEast(helper, base);
        helper.assertTrue(
                unloaded != null,
                "в пределах 256 чанков к востоку обязана найтись незагруженная область: иначе тест не проверяет пропуск");
        SpiritEntity far = ModEntities.SPIRIT.get().create(helper.getLevel());
        far.setPos(unloaded.getX() - 24, base.getY(), base.getZ());
        HazardScript script = new HazardScript(far, PerceptionParams.hazards(Map.of("radius", ScanRadius.MAX)));
        int loadedBefore = helper.getLevel().getChunkSource().getLoadedChunksCount();

        int ticks = 0;
        while (script.tick() == ScriptStatus.RUNNING) {
            ticks++;
            helper.assertTrue(ticks < 400, "sense_hazard радиуса 32 обязан закончиться за конечное число порций");
        }

        int loadedAfter = helper.getLevel().getChunkSource().getLoadedChunksCount();
        BlockCoord center = new BlockCoord(far.getBlockX(), far.getBlockY(), far.getBlockZ());
        BlockBox cube = BlockBox.cube(center, ScanRadius.MAX)
                .clampY(helper.getLevel().getMinBuildHeight(), helper.getLevel().getMaxBuildHeight() - 1)
                .orElseThrow();
        int totalSlices = SectionCursor.of(cube, center).total();
        helper.assertTrue(
                script.maxSlicesPerTick() == ScanTuning.SLICES_PER_TICK,
                "за тик sense_hazard обязан брать ровно " + ScanTuning.SLICES_PER_TICK + " среза: больше значит, что "
                        + "куб читается не порциями, меньше — что порция урезана, а взято "
                        + script.maxSlicesPerTick());
        helper.assertTrue(
                ticks + 1 == (totalSlices + ScanTuning.SLICES_PER_TICK - 1) / ScanTuning.SLICES_PER_TICK,
                "куб из " + totalSlices + " срезов обязан читаться порциями по " + ScanTuning.SLICES_PER_TICK
                        + ", тиков потребовалось " + (ticks + 1));
        helper.assertTrue(
                loadedAfter == loadedBefore,
                "sense_hazard не должен грузить чанки: было " + loadedBefore + ", стало " + loadedAfter);
        helper.assertTrue(
                script.blockReads() > 0, "тест не доказывает порции: ни одного чтения загруженных блоков не было");
        far.discard();
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    private static void frozen(Mob mob) {
        mob.setNoAi(true);
        mob.setPersistenceRequired();
    }

    private static int countOf(PerceptionResult result, String blockId) {
        return result.blocks().stream()
                .filter(entry -> entry.item().equals(blockId))
                .mapToInt(ItemCount::count)
                .sum();
    }

    private static OptionalDouble nearestDistance(PerceptionResult result, HazardType type) {
        return result.hazards().stream()
                .filter(hazard -> hazard.type() == type)
                .mapToDouble(HazardRef::distance)
                .min();
    }
}
