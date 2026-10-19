package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.movement.KeepDistanceScript;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MovementDistanceGameTests {
    private static final int DISTANCE = 5;
    private static final int RETREAT_LIMIT_TICKS = 100;
    private static final int APPROACH_LIMIT_TICKS = 200;
    private static final int HOLD_TICKS = 40;
    private static final double APPROACH_BAND = 1.0;
    private static final int SPIRITS = 20;
    private static final int SCALE_TICKS = 100;
    private static final int SCALE_DISTANCE = 2;
    private static final double RING_RADIUS = 2.5;

    private MovementDistanceGameTests() {}

    @GameTest(template = "arena", batch = "move_keep_close", timeoutTicks = 300)
    public static void keepDistance_TargetTwoBlocksAway_BacksOffToFiveAndHoldsForFortyTicks(GameTestHelper helper) {
        MovementScene.prepare(helper);
        SpiritEntity spirit = MovementScene.spirit(helper, new BlockPos(5, 1, 4));
        ServerPlayer player = MovementScene.mockPlayer(helper, new BlockPos(7, 1, 4));
        SpiritRun run = SpiritRun.launch(helper, spirit, keepCall(player, DISTANCE), MovementScene.SETTLE_TICKS);
        int[] reachedAt = {-1};
        double[] closestAfterReach = {Double.MAX_VALUE};
        helper.onEachTick(() -> {
            if (spirit.tickCount < MovementScene.SETTLE_TICKS) {
                return;
            }
            double distance = spirit.position().distanceTo(player.position());
            if (reachedAt[0] < 0 && distance >= DISTANCE) {
                reachedAt[0] = run.spiritTicks();
            }
            if (reachedAt[0] >= 0) {
                closestAfterReach[0] = Math.min(closestAfterReach[0], distance);
            }
            if (reachedAt[0] < 0 && run.spiritTicks() > RETREAT_LIMIT_TICKS) {
                helper.fail("за " + RETREAT_LIMIT_TICKS + " тиков дух не отошёл на " + DISTANCE + ": расстояние "
                        + distance);
            }
        });

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(reachedAt[0] >= 0, "дух ещё не отошёл на дистанцию " + DISTANCE);
            helper.assertTrue(
                    run.spiritTicks() >= reachedAt[0] + HOLD_TICKS,
                    "после отхода дистанция должна продержаться " + HOLD_TICKS + " тиков");
            helper.assertTrue(
                    closestAfterReach[0] >= DISTANCE,
                    "дистанция " + DISTANCE + " обязана держаться, а дух подошёл на " + closestAfterReach[0]);
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.RUNNING,
                    "keep_distance не заканчивается, пока жива цель: статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет"));
            MovementScene.removePlayer(helper, player);
            MovementScene.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "move_keep_far", timeoutTicks = 400)
    public static void keepDistance_TargetSevenBlocksAway_ApproachesToFivePlusMinusOneAndHolds(GameTestHelper helper) {
        MovementScene.prepare(helper);
        SpiritEntity spirit = MovementScene.spirit(helper, new BlockPos(1, 1, 4));
        ServerPlayer player = MovementScene.mockPlayer(helper, new BlockPos(8, 1, 4));
        SpiritRun run = SpiritRun.launch(helper, spirit, keepCall(player, DISTANCE), MovementScene.SETTLE_TICKS);
        int[] reachedAt = {-1};
        double[] widest = {0};
        double[] closest = {Double.MAX_VALUE};
        helper.onEachTick(() -> {
            if (spirit.tickCount < MovementScene.SETTLE_TICKS) {
                return;
            }
            double distance = spirit.position().distanceTo(player.position());
            boolean inBand = Math.abs(distance - DISTANCE) <= APPROACH_BAND;
            if (reachedAt[0] < 0 && inBand) {
                reachedAt[0] = run.spiritTicks();
            }
            if (reachedAt[0] >= 0) {
                widest[0] = Math.max(widest[0], distance);
                closest[0] = Math.min(closest[0], distance);
            }
            if (reachedAt[0] < 0 && run.spiritTicks() > APPROACH_LIMIT_TICKS) {
                helper.fail("за " + APPROACH_LIMIT_TICKS + " тиков дух не подошёл на " + DISTANCE + " плюс-минус "
                        + APPROACH_BAND + ": расстояние " + distance);
            }
        });

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(reachedAt[0] >= 0, "дух ещё не подошёл на дистанцию " + DISTANCE);
            helper.assertTrue(
                    run.spiritTicks() >= reachedAt[0] + HOLD_TICKS,
                    "после подхода дистанция должна продержаться " + HOLD_TICKS + " тиков");
            helper.assertTrue(
                    closest[0] >= DISTANCE - APPROACH_BAND && widest[0] <= DISTANCE + APPROACH_BAND,
                    "дистанция обязана держаться в пределах " + DISTANCE + " плюс-минус " + APPROACH_BAND
                            + ", а была от " + closest[0] + " до " + widest[0]);
            MovementScene.removePlayer(helper, player);
            MovementScene.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "move_keep_scale", timeoutTicks = 300)
    public static void keepDistance_TwentySpiritsHundredTicks_AskTheWorldOncePerDecisionPeriod(GameTestHelper helper) {
        MovementScene.prepare(helper);
        ServerPlayer player = MovementScene.mockPlayer(helper, new BlockPos(4, 1, 4));
        List<SpiritEntity> spirits = new ArrayList<>();
        for (int i = 0; i < SPIRITS; i++) {
            double angle = 2 * Math.PI * i / SPIRITS;
            spirits.add(helper.spawn(
                    ModEntities.SPIRIT.get(),
                    new Vec3(4.5 + RING_RADIUS * Math.cos(angle), 1, 4.5 + RING_RADIUS * Math.sin(angle))));
        }
        int[] startedAt = {-1};
        helper.onEachTick(() -> {
            if (startedAt[0] < 0
                    && spirits.stream().allMatch(spirit -> spirit.tickCount >= MovementScene.SETTLE_TICKS)) {
                startedAt[0] = spirits.get(0).tickCount;
                spirits.forEach(spirit -> spirit.startTask(keepCall(player, SCALE_DISTANCE)));
            }
        });
        int perSpiritBudget = SCALE_TICKS / KeepDistanceScript.DECISION_PERIOD_TICKS + 1;

        helper.succeedWhen(() -> {
            helper.assertTrue(startedAt[0] >= 0, "духи ещё не получили задачу");
            helper.assertTrue(
                    spirits.get(0).tickCount >= startedAt[0] + SCALE_TICKS,
                    "наблюдение за " + SCALE_TICKS + " тиками ещё не закончилось");
            helper.assertTrue(
                    KeepDistanceScript.DECISION_PERIOD_TICKS == 10,
                    "период решения keep_distance — 10 тиков (решение главного агента к чеклисту 24), в коде "
                            + KeepDistanceScript.DECISION_PERIOD_TICKS);
            int total = 0;
            for (SpiritEntity spirit : spirits) {
                helper.assertTrue(
                        spirit.taskStatus().orElse(null) == RunStatus.RUNNING,
                        "keep_distance за живой целью работает все " + SCALE_TICKS + " тиков, а статус "
                                + spirit.taskStatus().map(RunStatus::name).orElse("нет") + ", причина "
                                + spirit.taskFailureReason().orElse("нет")
                                + ": упавший скрипт не спрашивает мир, и счёт запросов ничего бы не доказывал");
                helper.assertTrue(
                        spirit.entityQueries() >= 1 && spirit.entityQueries() <= perSpiritBudget,
                        "дух спрашивает мир раз в период решения: от 1 до " + perSpiritBudget + " запросов за "
                                + SCALE_TICKS + " тиков, а задал " + spirit.entityQueries());
                total += spirit.entityQueries();
            }
            helper.assertTrue(
                    total <= SPIRITS * perSpiritBudget,
                    "двадцать духов за " + SCALE_TICKS + " тиков обязаны уложиться в " + SPIRITS * perSpiritBudget
                            + " запросов мира, а задали " + total);
            MovementScene.removePlayer(helper, player);
            MovementScene.finish(helper);
        });
    }

    private static TaskNode keepCall(ServerPlayer player, int distance) {
        return MovementScene.call(
                "keep_distance", Map.of("target", player.getName().getString(), "distance", distance));
    }
}
