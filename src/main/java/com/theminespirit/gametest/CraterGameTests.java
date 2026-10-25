package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.building.Crater;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.crater.CraterStore;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CraterGameTests {
    private static final int SETTLE_TICKS = 5;
    private static final BlockPos CENTER = new BlockPos(4, 1, 4);
    private static final int RADIUS = 2;
    private static final int PLATFORM_Y = 1;

    private CraterGameTests() {}

    private static void buildPlatform(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        for (int x = 1; x <= 7; x++) {
            for (int z = 1; z <= 7; z++) {
                helper.setBlock(new BlockPos(x, PLATFORM_Y, z), Blocks.STONE);
            }
        }
    }

    private static Crater addCrater(GameTestHelper helper, int radius) {
        ServerLevel level = helper.getLevel();
        int groundLevel = helper.absolutePos(CENTER).getY();
        Crater crater =
                new Crater(UUID.randomUUID(), toAbsolute(helper, CENTER), radius, groundLevel, level.getGameTime());
        CraterStore.get(level).add(crater);
        return crater;
    }

    private static BlockCoord toAbsolute(GameTestHelper helper, BlockPos relative) {
        BlockPos absolute = helper.absolutePos(relative);
        return new BlockCoord(absolute.getX(), absolute.getY(), absolute.getZ());
    }

    @SceneStep(template = "arena", timeoutTicks = 200)
    public static void explosion_CreeperNearHome_RecordsCrater(GameTestHelper helper) {
        buildPlatform(helper);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(2, 2, 2));
        spirit.setHome(new BlockCoord(
                helper.absolutePos(CENTER).getX(),
                helper.absolutePos(CENTER).getY(),
                helper.absolutePos(CENTER).getZ()));
        int before = CraterStore.get(helper.getLevel()).size();

        Creeper creeper = helper.spawn(EntityType.CREEPER, CENTER);
        BlockPos absolute = helper.absolutePos(CENTER);
        helper.getLevel()
                .explode(
                        creeper,
                        absolute.getX() + 0.5,
                        absolute.getY() + 0.5,
                        absolute.getZ() + 0.5,
                        2.0f,
                        Level.ExplosionInteraction.TNT);

        helper.succeedWhen(() -> {
            int after = CraterStore.get(helper.getLevel()).size();
            helper.assertTrue(
                    after == before + 1,
                    "Взрыв крипера рядом с домом обязан запомнить ровно одну воронку, было " + before + ", стало "
                            + after);
            GameTestFixtures.reset(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void fillCrater_EnoughDirt_LevelsSurface(GameTestHelper helper) {
        buildPlatform(helper);
        helper.setBlock(CENTER, Blocks.AIR);
        helper.setBlock(new BlockPos(CENTER.getX() + 1, PLATFORM_Y, CENTER.getZ()), Blocks.AIR);
        helper.setBlock(new BlockPos(CENTER.getX(), PLATFORM_Y, CENTER.getZ() + 1), Blocks.AIR);
        Crater crater = addCrater(helper, RADIUS);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(2, 2, 2));
        spirit.getInventory().setItem(0, new ItemStack(Items.DIRT, 30));
        SpiritRun run = SpiritRun.launch(helper, spirit, fillCall(crater), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "С 30 землёй воронка из 3 клеток обязана засыпаться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            int holes = countAirAtPlatform(helper);
            helper.assertTrue(holes == 0, "После засыпки на уровне земли не должно остаться пустот, осталось " + holes);
            GameTestFixtures.reset(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void fillCrater_TooLittleDirt_PartialFillWithResult(GameTestHelper helper) {
        buildPlatform(helper);
        for (int x = CENTER.getX() - 1; x <= CENTER.getX() + 1; x++) {
            for (int z = CENTER.getZ() - 1; z <= CENTER.getZ() + 1; z++) {
                if (x == CENTER.getX() && z == CENTER.getZ()) {
                    continue;
                }
                helper.setBlock(new BlockPos(x, PLATFORM_Y, z), Blocks.AIR);
            }
        }
        Crater crater = addCrater(helper, RADIUS);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(2, 2, 2));
        spirit.getInventory().setItem(0, new ItemStack(Items.DIRT, 2));
        SpiritRun run = SpiritRun.launch(helper, spirit, fillCall(crater), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "Нехватка земли не роняет задачу: частичная засыпка обязана завершиться успехом, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            String result =
                    String.valueOf(spirit.taskResult().map(r -> r.jsonValue()).orElse("нет итога"));
            helper.assertTrue(
                    result.contains("placed=2"), "Итог обязан назвать 2 засыпанные клетки, фактически: " + result);
            helper.assertTrue(result.contains("missing="), "Итог обязан назвать нехватку земли, фактически: " + result);
            int holes = countAirAtPlatform(helper);
            helper.assertTrue(holes > 0, "Земли мало: часть ямы обязана остаться незасыпанной, пустот " + holes);
            GameTestFixtures.reset(helper);
        });
    }

    private static ScriptCall fillCall(Crater crater) {
        return new ScriptCall("fill_crater", Map.of("crater", crater.id().toString()));
    }

    private static int countAirAtPlatform(GameTestHelper helper) {
        int holes = 0;
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (dx * dx + dz * dz > RADIUS * RADIUS) {
                    continue;
                }
                BlockPos cell = new BlockPos(CENTER.getX() + dx, PLATFORM_Y, CENTER.getZ() + dz);
                if (helper.getBlockState(cell).isAir()) {
                    holes++;
                }
            }
        }
        return holes;
    }
}
