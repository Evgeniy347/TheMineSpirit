package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MineBuildGameTests {
    private static final int SETTLE_TICKS = 5;
    private static final int TOP = 12;
    private static final int BOTTOM = 4;
    private static final int LANE_Z = 5;
    private static final int FIRST_X = 1;
    private static final int STEPS = TOP - BOTTOM + 1;

    private MineBuildGameTests() {}

    private static void rock(GameTestHelper helper) {
        GameTestFixtures.reset(helper, 16);
        for (int x = 0; x <= 10; x++) {
            for (int y = 1; y <= TOP; y++) {
                for (int z = 2; z <= 8; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
    }

    private static SpiritEntity miner(GameTestHelper helper) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(FIRST_X, TOP + 1, LANE_Z));
        spirit.getInventory().setItem(0, new ItemStack(Items.IRON_PICKAXE));
        spirit.getInventory().setItem(1, new ItemStack(Items.COBBLESTONE, 16));
        return spirit;
    }

    private static ScriptCall descent(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(FIRST_X, TOP, LANE_Z - 1));
        return new ScriptCall(
                "build_schematic",
                Map.of(
                        "file", "mine_descent_v1",
                        "x", origin.getX(),
                        "y", origin.getY(),
                        "z", origin.getZ(),
                        "segments", STEPS));
    }

    private static BlockPos step(int k, int dy) {
        return new BlockPos(FIRST_X + k, TOP - k + dy, LANE_Z);
    }

    private static void assertPassageOpen(GameTestHelper helper) {
        for (int k = 0; k < STEPS; k++) {
            for (int dy = 1; dy <= 3; dy++) {
                BlockState state = helper.getBlockState(step(k, dy));
                helper.assertTrue(
                        state.isAir() && state.getFluidState().isEmpty(),
                        "клетка спуска " + step(k, dy) + " обязана быть сухим воздухом: по ней дух ходит вверх и "
                                + "вниз, фактически " + state);
            }
            helper.assertTrue(
                    !helper.getBlockState(step(k, 0)).isAir(),
                    "пол ступени " + step(k, 0) + " обязан быть твёрдым: без него дух провалится");
        }
    }

    private static Map<String, Object> data(GameTestHelper helper, SpiritEntity spirit) {
        helper.assertTrue(
                spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                "проходка спуска обязана завершиться успехом, статус "
                        + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                        + spirit.taskFailureReason().orElse("нет"));
        return ((ScriptResult.Data) spirit.taskResult().orElseThrow()).fields();
    }

    // [флак] спуск через пещеру: пол и стенки над пустотой, 9 ступеней и выход; замерено ~650 тиков, запас 2×
    @SceneStep(template = "mine_arena", timeoutTicks = 1600)
    public static void mineDescent_CaveUnderSteps_PlacesFloorAndWallsInAir(GameTestHelper helper) {
        rock(helper);
        for (int x = FIRST_X + 4; x <= FIRST_X + 6; x++) {
            for (int y = 2; y <= TOP - 3; y++) {
                for (int z = 2; z <= 8; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
        SpiritEntity spirit = miner(helper);
        spirit.getInventory().setItem(1, new ItemStack(Items.COBBLESTONE, 64));
        SpiritRun run = SpiritRun.launch(helper, spirit, descent(helper), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            data(helper, spirit);
            assertPassageOpen(helper);
            for (int k = 0; k < STEPS; k++) {
                for (int dy = 1; dy <= 3; dy++) {
                    for (int side : new int[] {-1, 1}) {
                        BlockPos wall = step(k, dy).offset(0, 0, side);
                        helper.assertTrue(
                                !helper.getBlockState(wall).isAir(),
                                "стенка спуска " + wall + " обязана быть твёрдой и над пещерой (решение владельца "
                                        + "23.10): иначе дух и игрок падают в пропасть, скелеты стреляют в проход");
                    }
                }
            }
            GameTestFixtures.finish(helper, 16);
        });
    }

    // [флак] спуск с водой в стене и потолке: пробки и 9 ступеней с выходом; замерено ~750 тиков, запас 2×
    @SceneStep(template = "mine_arena", timeoutTicks = 1600)
    public static void mineDescent_WaterBehindWall_PlugsAndSpiritNeverUnderWater(GameTestHelper helper) {
        rock(helper);
        BlockPos wallWater = step(3, 1).south();
        BlockPos ceilingWater = step(5, 4);
        helper.setBlock(wallWater, Blocks.WATER);
        helper.setBlock(ceilingWater, Blocks.WATER);
        SpiritEntity spirit = miner(helper);
        AtomicBoolean wet = new AtomicBoolean();
        helper.onEachTick(() -> {
            if (spirit.isUnderWater() || spirit.isInWater()) {
                wet.set(true);
            }
        });
        SpiritRun run = SpiritRun.launch(helper, spirit, descent(helper), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            Map<String, Object> data = data(helper, spirit);
            assertPassageOpen(helper);
            helper.assertTrue(
                    helper.getBlockState(wallWater).is(Blocks.COBBLESTONE)
                            && helper.getBlockState(ceilingWater).is(Blocks.COBBLESTONE),
                    "источники воды у прохода обязаны быть перекрыты булыжником до вскрытия (INC-19), стена "
                            + helper.getBlockState(wallWater) + ", потолок " + helper.getBlockState(ceilingWater));
            helper.assertTrue(
                    !wet.get(),
                    "дух ни разу не оказался в воде: проход не затапливается, копать в воду нельзя; итог " + data);
            GameTestFixtures.finish(helper, 16);
        });
    }
}
