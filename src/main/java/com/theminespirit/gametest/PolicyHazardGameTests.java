package com.theminespirit.gametest;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.policy.PathingConfigurator;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PolicyHazardGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BlockPos CENTER = new BlockPos(4, 1, 4);
    private static final int REFLEX_LIMIT_TICKS = 20;
    private static final int SETTLE_TICKS = 2;
    private static final BlockPos GOTO_START = new BlockPos(1, 1, 4);
    private static final BlockPos GOTO_TARGET = new BlockPos(8, 1, 4);

    private PolicyHazardGameTests() {
    }

    private static void prepare(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
    }

    private static BlockPos cellOf(GameTestHelper helper, SpiritEntity spirit) {
        return BlockPos.containing(helper.relativeVec(spirit.position()));
    }

    private static ScriptCall gotoCall(GameTestHelper helper, BlockPos relative) {
        BlockPos absolute = helper.absolutePos(relative);
        return new ScriptCall("goto", Map.of("x", absolute.getX(), "y", absolute.getY(), "z", absolute.getZ()));
    }

    @GameTest(template = "arena", batch = "policy_lava_beside", timeoutTicks = 80)
    public static void lavaBeside_SpiritNextToLava_StepsAwayWithoutDamage(GameTestHelper helper) {
        prepare(helper);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), CENTER);
        BlockPos lava = new BlockPos(5, 1, 4);
        PolicyScene scene = PolicyScene.launch(helper, spirit, SETTLE_TICKS, () -> helper.setBlock(lava, Blocks.LAVA));

        helper.succeedWhen(() -> {
            scene.assertStarted();
            BlockPos at = cellOf(helper, spirit);
            helper.assertTrue(Math.abs(at.getX() - lava.getX()) >= 2 || Math.abs(at.getZ() - lava.getZ()) >= 2,
                    "лава вплотную: дух обязан отойти минимум на 2 клетки, стоит в " + at.toShortString());
            helper.assertTrue(scene.ticks() <= REFLEX_LIMIT_TICKS,
                    "отход от лавы обязан уложиться в " + REFLEX_LIMIT_TICKS + " тиков духа, ушло " + scene.ticks());
            helper.assertTrue(scene.minHealth() >= spirit.getMaxHealth(),
                    "рядом с лавой дух не должен терять здоровье: минимум " + scene.minHealth() + " из " + spirit.getMaxHealth());
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "arena", batch = "policy_lava_puddle", timeoutTicks = 80)
    public static void lavaPuddle_SpiritStandingInLava_ClimbsOutAlive(GameTestHelper helper) {
        prepare(helper);
        helper.setBlock(CENTER, Blocks.LAVA);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), CENTER);
        PolicyScene scene = PolicyScene.launch(helper, spirit, 1, () -> {
        });

        helper.succeedWhen(() -> {
            scene.assertStarted();
            helper.assertTrue(!spirit.isInLava(), "дух в луже лавы обязан выбраться: он всё ещё в лаве на тике " + scene.ticks());
            helper.assertTrue(scene.ticks() <= REFLEX_LIMIT_TICKS,
                    "выход из лавы обязан уложиться в " + REFLEX_LIMIT_TICKS + " тиков духа, ушло " + scene.ticks());
            helper.assertTrue(spirit.isAlive() && spirit.getHealth() > 0, "дух обязан остаться живым после выхода из лавы");
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "arena", batch = "policy_burning_water", timeoutTicks = 200)
    public static void burning_SpiritWithWaterFourBlocksAway_WalksIntoWaterAndStopsBurning(GameTestHelper helper) {
        prepare(helper);
        helper.setBlock(8, 1, 4, Blocks.WATER);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), CENTER);
        boolean[] reachedWater = {false};
        PolicyScene scene = PolicyScene.launch(helper, spirit, SETTLE_TICKS, () -> spirit.setRemainingFireTicks(400),
                observed -> {
                    reachedWater[0] |= observed.isInWater();
                    return null;
                });

        helper.succeedWhen(() -> {
            scene.assertStarted();
            helper.assertTrue(reachedWater[0], "горящий дух обязан дойти до воды в 4 блоках, тик духа " + scene.ticks());
            helper.assertTrue(!spirit.isOnFire(), "после воды огонь на духе обязан погаснуть, тик духа " + scene.ticks());
            helper.assertTrue(scene.ticks() <= 120, "тушение обязано уложиться в 120 тиков духа, ушло " + scene.ticks());
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "arena", batch = "policy_lava_moat", timeoutTicks = 400)
    public static void gotoAcrossLavaMoat_NoWayAround_FailsWithoutTouchingLava(GameTestHelper helper) {
        prepare(helper);
        for (int z = 0; z <= 8; z++) {
            helper.setBlock(6, 0, z, Blocks.LAVA);
            for (int y = 1; y <= 3; y++) {
                helper.setBlock(0, y, z, Blocks.STONE);
            }
        }
        for (int x = 0; x <= 5; x++) {
            for (int y = 1; y <= 3; y++) {
                helper.setBlock(x, y, 0, Blocks.STONE);
                helper.setBlock(x, y, 8, Blocks.STONE);
            }
        }
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(2, 1, 4));
        PolicyScene scene = PolicyScene.launch(helper, spirit, 1, () -> {
        }, observed -> {
            Vec3 at = helper.relativeVec(observed.position());
            if (observed.isInLava() || at.x > 5.0) {
                StringBuilder types = new StringBuilder();
                for (int x = 3; x <= 7; x++) {
                    types.append(x).append('=').append(WalkNodeEvaluator.getPathTypeStatic(observed, helper.absolutePos(new BlockPos(x, 1, 4)))).append(' ');
                }
                return "дух подошёл к рву с лавой ближе чем на клетку: x=" + at.x + ", y=" + at.y + ", z=" + at.z + ", типы клеток " + types;
            }
            return null;
        });
        SpiritRun run = SpiritRun.launch(helper, spirit, gotoCall(helper, new BlockPos(8, 1, 4)), 1);

        helper.succeedWhen(() -> {
            run.assertStarted();
            scene.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "цель за рвом с лавой недостижима: задача обязана провалиться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(reason.startsWith("застрял") || reason.startsWith("не дошёл"),
                    "причина провала обязана называть, что дух не дошёл: «" + reason + "»");
            GameTestFixtures.reset(helper);
        });
    }

    private static void hazardOnTheWay(GameTestHelper helper, Block hazard, Block under, boolean hazardIsFloor) {
        prepare(helper);
        BlockPos cell = new BlockPos(4, hazardIsFloor ? 0 : 1, 4);
        if (under != null) {
            helper.setBlock(4, 0, 4, under);
        }
        helper.setBlock(cell, hazard);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), GOTO_START);
        BlockPos[] stepped = {null};
        PolicyScene scene = PolicyScene.launch(helper, spirit, 1, () -> {
        }, observed -> {
            BlockPos at = cellOf(helper, observed);
            if (at.getX() == 4 && at.getZ() == 4) {
                stepped[0] = at;
            }
            return null;
        });
        SpiritRun run = SpiritRun.launch(helper, spirit, gotoCall(helper, GOTO_TARGET), 1);

        helper.succeedWhen(() -> {
            run.assertStarted();
            scene.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "цель достижима в обход опасного блока: статус " + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи")
                            + ", причина " + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(stepped[0] == null, "дух наступил на опасный блок " + hazard + ": клетка " + stepped[0]);
            helper.assertTrue(scene.minHealth() >= spirit.getMaxHealth(),
                    "обход опасного блока " + hazard + " без урона, минимум здоровья " + scene.minHealth());
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "arena", batch = "policy_path_cactus", timeoutTicks = 300)
    public static void pathPastCactus_GotoAcrossFloor_GoesAroundWithoutDamage(GameTestHelper helper) {
        hazardOnTheWay(helper, Blocks.CACTUS, Blocks.SAND, false);
    }

    @GameTest(template = "arena", batch = "policy_path_magma", timeoutTicks = 300)
    public static void pathPastMagma_GotoAcrossFloor_GoesAroundWithoutDamage(GameTestHelper helper) {
        hazardOnTheWay(helper, Blocks.MAGMA_BLOCK, null, true);
    }

    @GameTest(template = "arena", batch = "policy_path_cobweb", timeoutTicks = 300)
    public static void pathPastCobweb_GotoAcrossFloor_GoesAroundWithoutStepping(GameTestHelper helper) {
        hazardOnTheWay(helper, Blocks.COBWEB, null, false);
    }

    @GameTest(template = "arena", batch = "policy_path_berries", timeoutTicks = 300)
    public static void pathPastBerries_GotoAcrossFloor_GoesAroundWithoutDamage(GameTestHelper helper) {
        hazardOnTheWay(helper, Blocks.SWEET_BERRY_BUSH, Blocks.DIRT, false);
    }

    @GameTest(template = "arena", batch = "policy_path_snow", timeoutTicks = 300)
    public static void pathPastPowderSnow_GotoAcrossFloor_GoesAroundWithoutStepping(GameTestHelper helper) {
        hazardOnTheWay(helper, Blocks.POWDER_SNOW, null, false);
    }

    private static void cliff(GameTestHelper helper, int drop, RunStatus expected) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 8; z++) {
                for (int y = 1; y <= drop; y++) {
                    helper.setBlock(x, y, z, Blocks.STONE);
                }
            }
        }
        int startY = drop + 1;
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(2, startY, 4));
        double[] lowest = {Double.MAX_VALUE};
        PolicyScene scene = PolicyScene.launch(helper, spirit, 1, () -> {
        }, observed -> {
            lowest[0] = Math.min(lowest[0], helper.relativeVec(observed.position()).y);
            return null;
        });
        SpiritRun run = SpiritRun.launch(helper, spirit, gotoCall(helper, new BlockPos(7, 1, 4)), 1);

        helper.succeedWhen(() -> {
            run.assertStarted();
            scene.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == expected,
                    "обрыв в " + drop + " блока: ждали статус " + expected + ", получен "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина " + spirit.taskFailureReason().orElse("нет"));
            if (expected == RunStatus.FAILED) {
                helper.assertTrue(lowest[0] >= startY - 2,
                        "дух не должен падать глубже 2 блоков от старта " + startY + ", самая низкая точка " + lowest[0]);
            } else {
                helper.assertTrue(lowest[0] <= 1.5, "спуск на 2 блока обязан быть выполнен: самая низкая точка " + lowest[0]);
            }
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "arena", batch = "policy_cliff_three", timeoutTicks = 400)
    public static void cliffOfThreeBlocks_GotoDownward_FailsWithoutFalling(GameTestHelper helper) {
        cliff(helper, 3, RunStatus.FAILED);
    }

    @GameTest(template = "arena", batch = "policy_cliff_two", timeoutTicks = 400)
    public static void cliffOfTwoBlocks_GotoDownward_DescendsAndArrives(GameTestHelper helper) {
        cliff(helper, 2, RunStatus.SUCCEEDED);
    }

    @GameTest(template = "arena", batch = "policy_path_table", timeoutTicks = 20)
    public static void pathTable_RealSpirit_BlocksEveryHazardPathType(GameTestHelper helper) {
        prepare(helper);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), CENTER);
        helper.runAfterDelay(2, () -> {
            for (PathType type : PathingConfigurator.BLOCKED_TYPES) {
                helper.assertTrue(spirit.getPathfindingMalus(type) < 0,
                        "тип клетки " + type + " обязан быть закрыт для пути духа, штраф " + spirit.getPathfindingMalus(type));
            }
            helper.assertTrue(spirit.getPathfindingMalus(PathType.WATER) == PathingConfigurator.WATER_COST,
                    "вода обязана иметь высокую цену " + PathingConfigurator.WATER_COST + ", а не запрет; штраф " + spirit.getPathfindingMalus(PathType.WATER));
            helper.assertTrue(spirit.getMaxFallDistance() == 2, "предел спуска в пути 2 блока, а у духа " + spirit.getMaxFallDistance());
            GameTestFixtures.reset(helper);
            helper.succeed();
        });
    }

    @GameTest(template = "arena", batch = "policy_path_types", timeoutTicks = 20)
    public static void pathTypes_HazardBlocks_AreClosedForTheSpirit(GameTestHelper helper) {
        prepare(helper);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), CENTER);
        Block[][] cases = {{Blocks.LAVA, null}, {Blocks.FIRE, null}, {Blocks.CAMPFIRE, null}, {Blocks.CACTUS, Blocks.SAND},
                {Blocks.SWEET_BERRY_BUSH, Blocks.DIRT}, {Blocks.COBWEB, null}, {Blocks.POWDER_SNOW, null}, {Blocks.MAGMA_BLOCK, null}};
        helper.runAfterDelay(2, () -> {
            StringBuilder report = new StringBuilder();
            for (Block[] row : cases) {
                BlockPos at = new BlockPos(6, row[0] == Blocks.MAGMA_BLOCK ? 0 : 1, 6);
                if (row[1] != null) {
                    helper.setBlock(6, 0, 6, row[1]);
                }
                helper.setBlock(at, row[0]);
                BlockPos absolute = helper.absolutePos(at);
                PathType type = new PathfindingContext(helper.getLevel(), spirit).getPathTypeFromState(absolute.getX(), absolute.getY(), absolute.getZ());
                float malus = spirit.getPathfindingMalus(type);
                report.append(row[0]).append("→").append(type).append("(").append(malus).append(") ");
                helper.assertTrue(malus < 0 || type == PathType.OPEN || type == PathType.WALKABLE,
                        "опасный блок " + row[0] + " имеет тип " + type + " со штрафом " + malus + ": путь пойдёт через него. Все: " + report);
            }
            LOGGER.info("PATHTYPES {}", report);
            GameTestFixtures.reset(helper);
            helper.succeed();
        });
    }
}
