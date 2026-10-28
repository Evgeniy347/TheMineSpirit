package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.tactics.CombatTuning;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.adapter.RetreatPlanner;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Arrays;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
final class CombatFleeGameTests {
    private CombatFleeGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void flee_HuskClose_SpiritEscapesWithoutFighting(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));
        spirit.getInventory().addItem(new ItemStack(Items.IRON_SWORD));
        Husk husk = TacticsScene.watcher(helper, EntityType.HUSK, new BlockPos(8, 1, 5));
        for (int x = -15; x <= 0; x++) {
            for (int z = 3; z <= 7; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.COBBLESTONE);
            }
        }

        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("flee", Map.of()), 0);
        double startDistance = new Point(spirit.getX(), spirit.getY(), spirit.getZ())
                .distanceTo(new Point(husk.getX(), husk.getY(), husk.getZ()));
        double[] closestSeen = {Double.MAX_VALUE};
        int[] huskHealthAt = {20, 20, 20, 20, 20};
        helper.onEachTick(() -> {
            int t = (int) helper.getTick();
            if (t < huskHealthAt.length) {
                huskHealthAt[t] = (int) husk.getHealth();
            }
            if (!husk.isDeadOrDying() && !husk.isRemoved()) {
                double distance = new Point(spirit.getX(), spirit.getY(), spirit.getZ())
                        .distanceTo(new Point(husk.getX(), husk.getY(), husk.getZ()));
                closestSeen[0] = Math.min(closestSeen[0], distance);
            }
        });

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "flee обязан увести духа из опасности и завершиться успехом, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет") + "; старт dist=" + startDistance
                            + ", min dist=" + closestSeen[0] + ", здоровье[0..4]: "
                            + Arrays.toString(huskHealthAt));
            double distance = new Point(spirit.getX(), spirit.getY(), spirit.getZ())
                    .distanceTo(new Point(husk.getX(), husk.getY(), husk.getZ()));
            helper.assertTrue(
                    distance > CombatTuning.DANGER_RADIUS,
                    "дух обязан убежать дальше CombatTuning.DANGER_RADIUS (" + CombatTuning.DANGER_RADIUS
                            + "), осталось: " + distance);
            helper.assertTrue(
                    spirit.tactics().combat().strikes() == 0,
                    "flee — бегство, а не бой: ударов обязано быть ноль, ударов: "
                            + spirit.tactics().combat().strikes());
            TacticsScene.finish(helper, spirit);
        });
    }

    // [флак] тайм-аут скрипта FLEE_SAFE/FLEE_TIMEOUT_TICKS=600 тиков: ловушка честно ждёт отказ по тайм-ауту
    @SceneStep(template = "arena", timeoutTicks = 900)
    public static void flee_TrappedInWalls_TimesOutWithTickCount(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));
        for (int y = 1; y <= 2; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    helper.setBlock(new BlockPos(5 + dx, y, 5 + dz), Blocks.COBBLESTONE);
                }
            }
        }
        TacticsScene.watcher(helper, EntityType.HUSK, new BlockPos(7, 1, 5));

        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("flee", Map.of()), 0);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "в ловушке flee обязан отказаться по тайм-ауту, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(
                    reason.contains("тайм-аут " + CatalogLimits.FLEE_TIMEOUT_TICKS),
                    "отказ обязан называть тайм-аут " + CatalogLimits.FLEE_TIMEOUT_TICKS + " тиков, причина: "
                            + reason);
            TacticsScene.finish(helper, spirit);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void retreat_HomeSet_SpiritReturnsToHome(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(1, 1, 9));
        BlockPos absoluteHome = helper.absolutePos(new BlockPos(9, 1, 1));
        spirit.setHome(new BlockCoord(absoluteHome.getX(), absoluteHome.getY(), absoluteHome.getZ()));
        Point home = new Point(absoluteHome.getX() + 0.5, absoluteHome.getY(), absoluteHome.getZ() + 0.5);

        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("retreat", Map.of()), 10);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "retreat обязан довести духа до дома и завершиться успехом, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            double distance = new Point(spirit.getX(), spirit.getY(), spirit.getZ()).distanceTo(home);
            helper.assertTrue(
                    distance <= RetreatPlanner.HOME_ARRIVAL + 0.25,
                    "дух обязан остановиться у дома (RetreatPlanner.HOME_ARRIVAL=" + RetreatPlanner.HOME_ARRIVAL
                            + "), расстояние: " + distance);
            TacticsScene.finish(helper, spirit);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 100)
    public static void retreat_NoHome_FailsWithNoPoint(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));

        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("retreat", Map.of()), 10);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "retreat без дома обязан отказаться, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(
                    reason.contains("точки отхода нет"),
                    "отказ обязан называть, что точки отхода нет, причина: " + reason);
            TacticsScene.finish(helper, spirit);
        });
    }
}
