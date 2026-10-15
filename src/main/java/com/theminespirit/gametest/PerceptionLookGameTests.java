package com.theminespirit.gametest;

import static com.theminespirit.gametest.PerceptionTestSupport.TEST_TIMEOUT_TICKS;
import static com.theminespirit.gametest.PerceptionTestSupport.assertSucceeded;
import static com.theminespirit.gametest.PerceptionTestSupport.coordOf;
import static com.theminespirit.gametest.PerceptionTestSupport.launch;
import static com.theminespirit.gametest.PerceptionTestSupport.spawnSpirit;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.perception.Finding;
import com.theminespirit.core.perception.Sighting;
import com.theminespirit.neoforge.LookAtScript;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
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
public final class PerceptionLookGameTests {
    private PerceptionLookGameTests() {
    }

    @GameTest(template = "arena", batch = "perception_look_block", timeoutTicks = TEST_TIMEOUT_TICKS)
    public static void lookAt_GoldBlockThreeBlocksAway_DescribesBlock(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(4, 1, 4, Blocks.GOLD_BLOCK);
        SpiritEntity spirit = spawnSpirit(helper, 1, 1, 4);
        BlockCoord target = coordOf(helper, 4, 1, 4);
        SpiritRun run = launch(helper, spirit, "look_at",
                Map.of("x", target.x(), "y", target.y(), "z", target.z()));

        helper.succeedWhen(() -> {
            assertSucceeded(helper, spirit, run, "look_at");
            Finding expected = new Finding("look_at", List.of(new Sighting("block", "minecraft:gold_block", target, "")));
            helper.assertTrue(expected.equals(spirit.finding().orElse(null)),
                    "look_at на золотой блок в трёх блоках обязан описать блок " + expected + ", а описал: "
                            + spirit.finding().orElse(null));
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "perception_look_mob", timeoutTicks = TEST_TIMEOUT_TICKS)
    public static void lookAt_CowTwoBlocksAway_DescribesAirAndMob(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        Mob cow = helper.spawn(EntityType.COW, new BlockPos(3, 1, 4));
        cow.setNoAi(true);
        cow.setPersistenceRequired();
        SpiritEntity spirit = spawnSpirit(helper, 1, 1, 4);
        BlockCoord target = coordOf(helper, 3, 1, 4);
        SpiritRun run = launch(helper, spirit, "look_at",
                Map.of("x", target.x(), "y", target.y(), "z", target.z()));

        helper.succeedWhen(() -> {
            assertSucceeded(helper, spirit, run, "look_at");
            Finding expected = new Finding("look_at", List.of(new Sighting("air", "minecraft:air", target, ""),
                    new Sighting("entity", "minecraft:cow", target, "")));
            helper.assertTrue(expected.equals(spirit.finding().orElse(null)),
                    "look_at на клетку с коровой обязан описать воздух и корову " + expected + ", а описал: "
                            + spirit.finding().orElse(null));
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "perception_look_container", timeoutTicks = TEST_TIMEOUT_TICKS)
    public static void lookAt_ChestOneBlockAway_DescribesBlockAndContainer(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(2, 1, 4, Blocks.CHEST);
        SpiritEntity spirit = spawnSpirit(helper, 1, 1, 4);
        BlockCoord target = coordOf(helper, 2, 1, 4);
        SpiritRun run = launch(helper, spirit, "look_at",
                Map.of("x", target.x(), "y", target.y(), "z", target.z()));

        helper.succeedWhen(() -> {
            assertSucceeded(helper, spirit, run, "look_at");
            Finding expected = new Finding("look_at", List.of(new Sighting("block", "minecraft:chest", target, ""),
                    new Sighting("container", "minecraft:chest", target, "")));
            helper.assertTrue(expected.equals(spirit.finding().orElse(null)),
                    "look_at на сундук рядом обязан описать блок и контейнер " + expected + ", а описал: "
                            + spirit.finding().orElse(null));
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "perception_look_unloaded", timeoutTicks = TEST_TIMEOUT_TICKS)
    public static void lookAt_UnloadedChunk_SkipsCellWithoutLoadingChunk(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        BlockPos base = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos unloaded = GameTestFixtures.firstUnloadedChunkEast(helper, base);
        helper.assertTrue(unloaded != null,
                "в пределах 256 чанков к востоку обязана найтись незагруженная область: иначе тест не проверяет пропуск");
        int chunkX = unloaded.getX() >> 4;
        int chunkZ = unloaded.getZ() >> 4;
        SpiritEntity looker = ModEntities.SPIRIT.get().create(helper.getLevel());
        looker.setPos(base.getX(), base.getY(), base.getZ());
        LookAtScript script = new LookAtScript(looker, new BlockCoord(unloaded.getX(), unloaded.getY(), unloaded.getZ()));

        ScriptStatus status = script.tick();

        Finding finding = looker.finding().orElse(null);
        helper.assertTrue(status == ScriptStatus.SUCCEEDED && finding != null && finding.sightings().size() == 1
                        && finding.sightings().get(0).kind().equals("unloaded"),
                "look_at на клетку в незагруженном чанке обязан завершиться успехом и сообщить пропуск (unloaded), "
                        + "а не воздух: статус " + status + ", находка " + finding);
        helper.assertTrue(!helper.getLevel().hasChunk(chunkX, chunkZ),
                "look_at не должен загружать чанк (" + chunkX + ", " + chunkZ + "): getChunk вместо getChunkNow "
                        + "превращает вопрос ИИ в загрузку мира");
        helper.assertTrue(script.blockReads() == 0,
                "в незагруженном чанке блок не читается: чтений " + script.blockReads());
        GameTestFixtures.finish(helper);
        helper.succeed();
    }
}
