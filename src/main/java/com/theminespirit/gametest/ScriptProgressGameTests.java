package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunProgress;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ScriptProgressGameTests {
    private static final BlockPos SPIRIT_START = new BlockPos(4, 1, 4);
    private static final int SETTLE_TICKS = 5;
    private static final int COBBLE_TARGET = 6;
    private static final int ORE_TARGET = 3;
    private static final int RADIUS = 16;

    private ScriptProgressGameTests() {
    }

    @GameTest(template = "arena", batch = "progress_gather", timeoutTicks = 400)
    public static void gatherProgress_CobblestoneHeap_DoneGrowsMonotonicallyTowardTarget(GameTestHelper helper) {
        heap(helper, Blocks.COBBLESTONE, 1, 3, 4, 6);
        SpiritEntity spirit = spiritWithPickaxe(helper);
        List<RunProgress> seen = recordProgress(helper, spirit);
        SpiritRun.launch(helper, spirit, new ScriptCall("gather", Map.of("block", "cobblestone", "count", COBBLE_TARGET, "radius", RADIUS)),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "gather шести булыжников обязан завершиться успехом: статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            assertCountedProgress(helper, seen, "gather", COBBLE_TARGET);
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "arena", batch = "progress_mine_vein", timeoutTicks = 400)
    public static void mineVeinProgress_ThreeIronOre_DoneGrowsMonotonicallyTowardTarget(GameTestHelper helper) {
        heap(helper, Blocks.IRON_ORE, 1, 3, 6, 6);
        SpiritEntity spirit = spiritWithPickaxe(helper);
        List<RunProgress> seen = recordProgress(helper, spirit);
        SpiritRun.launch(helper, spirit, new ScriptCall("mine_vein", Map.of("ore", "iron", "count", ORE_TARGET, "radius", RADIUS)),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "mine_vein трёх железных руд обязан завершиться успехом: статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            assertCountedProgress(helper, seen, "mine_vein", ORE_TARGET);
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "arena", batch = "progress_goto", timeoutTicks = 200)
    public static void gotoProgress_WalkingToCorner_HasStepButNoCounters(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        List<RunProgress> seen = recordProgress(helper, spirit);
        BlockPos target = helper.absolutePos(new BlockPos(7, 1, 7));
        SpiritRun.launch(helper, spirit,
                new ScriptCall("goto", Map.of("x", target.getX(), "y", target.getY(), "z", target.getZ())), 1);

        helper.succeedWhen(() -> {
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "goto в угол арены обязан завершиться успехом: статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            helper.assertTrue(!seen.isEmpty(), "Пока goto идёт, прогресс задачи обязан быть доступен");
            for (RunProgress progress : seen) {
                helper.assertTrue((progress.currentScript() == null || progress.currentScript().equals("goto")) && progress.done() == null
                        && progress.target() == null,
                        "У goto нет счётчика: ожидалось goto (или ещё не созданный скрипт) без done/target, получено " + progress);
            }
            GameTestFixtures.reset(helper);
        });
    }

    private static void assertCountedProgress(GameTestHelper helper, List<RunProgress> seen, String script,
            int expectedTarget) {
        List<RunProgress> named = seen.stream().filter(progress -> progress.currentScript() != null).toList();
        helper.assertTrue(!named.isEmpty(), "Пока " + script + " идёт, прогресс задачи обязан быть доступен");
        int previousDone = 0;
        int maxDone = 0;
        for (RunProgress progress : named) {
            helper.assertTrue(script.equals(progress.currentScript()) && progress.step() == 1
                    && progress.totalSteps() == 1,
                    "Прогресс обязан называть текущий скрипт " + script + " и шаг 1 из 1, получено " + progress);
            helper.assertTrue(progress.target() != null && progress.target() == expectedTarget,
                    "target обязан быть равен count скрипта (" + expectedTarget + "), получено " + progress);
            helper.assertTrue(progress.done() != null && progress.done() >= previousDone
                    && progress.done() <= expectedTarget,
                    "done обязан не убывать и не превышать target " + expectedTarget + ", получено " + progress
                            + " после done=" + previousDone);
            previousDone = progress.done();
            maxDone = Math.max(maxDone, progress.done());
        }
        helper.assertTrue(maxDone >= expectedTarget - 1,
                "За время работы done обязан дорасти минимум до " + (expectedTarget - 1) + " из " + expectedTarget
                        + ", максимум " + maxDone + " в " + named.size() + " замерах");
    }

    private static List<RunProgress> recordProgress(GameTestHelper helper, SpiritEntity spirit) {
        List<RunProgress> seen = new ArrayList<>();
        helper.onEachTick(() -> spirit.taskProgress().ifPresent(seen::add));
        return seen;
    }

    private static SpiritEntity spiritWithPickaxe(GameTestHelper helper) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);
        spirit.getInventory().setItem(0, new ItemStack(Items.IRON_PICKAXE));
        return spirit;
    }

    private static void heap(GameTestHelper helper, Block block, int fromX, int toX, int fromZ, int toZ) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        for (int x = fromX; x <= toX; x++) {
            for (int z = fromZ; z <= toZ; z++) {
                helper.setBlock(x, 1, z, block);
            }
        }
    }
}
