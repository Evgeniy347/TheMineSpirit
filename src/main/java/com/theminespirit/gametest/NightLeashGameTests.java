package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.Approach;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.tactics.CombatActuator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NightLeashGameTests {
    private static final BlockPos SPIRIT = new BlockPos(1, 1, 1);
    private static final BlockPos HOME = new BlockPos(-30, 1, 1);
    private static final BlockPos OUTSIDE = new BlockPos(3, 1, 1);
    private static final BlockPos FOLLOW_PLAYER = new BlockPos(7, 1, 1);
    private static final long NIGHT = 13000L;
    private static final long NOON = 6000L;
    private static final int SETTLEMENT_RADIUS = 32;
    private static final int OBSERVE_TICKS = 40;
    private static final String FOLLOW_PLAYER_NAME = "gt_night_leash_follow_player";
    private static final String COME_PLAYER_NAME = "gt_night_leash_come_player";

    private NightLeashGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 100)
    public static void goto_NightTargetOutsideSettlement_FailsWithReasonWithoutReaching(GameTestHelper helper) {
        MovementScene.prepare(helper);
        SpiritEntity spirit = MovementScene.spirit(helper, SPIRIT);
        spirit.setHome(MovementScene.coord(helper, HOME));
        GameTestFixtures.holdDayTime(helper, NIGHT);
        SpiritRun run = SpiritRun.launch(
                helper, spirit, MovementScene.pointCall(helper, "goto", OUTSIDE), MovementScene.SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "ночью цель за границей поселения обязана быть отвергнута, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет") + "; "
                            + MovementScene.facts(helper, spirit, NIGHT));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(
                    reason.contains("ночь: за границу поселения не выходим"),
                    "причина обязана говорить про ночь и границу поселения, фактически: " + reason);
            double toTarget = MovementScene.distanceTo(helper, spirit, OUTSIDE);
            helper.assertTrue(
                    toTarget > MovementScene.ARRIVAL, "отказанную цель не достигают: расстояние до неё " + toTarget);
            MovementScene.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 200)
    public static void goto_DayTargetOutsideSettlement_Arrives(GameTestHelper helper) {
        MovementScene.prepare(helper);
        SpiritEntity spirit = MovementScene.spirit(helper, SPIRIT);
        spirit.setHome(MovementScene.coord(helper, HOME));
        GameTestFixtures.holdDayTime(helper, NOON);
        SpiritRun run = SpiritRun.launch(
                helper, spirit, MovementScene.pointCall(helper, "goto", OUTSIDE), MovementScene.SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "днём граница поселения не держит: goto за радиус обязан дойти, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет") + ", причина "
                            + spirit.taskFailureReason().orElse("нет") + "; "
                            + MovementScene.facts(helper, spirit, NOON));
            double distance = MovementScene.distanceTo(helper, spirit, OUTSIDE);
            helper.assertTrue(
                    distance <= MovementScene.ARRIVAL,
                    "дух обязан встать у цели в пределах допуска " + MovementScene.ARRIVAL + ", расстояние "
                            + distance);
            MovementScene.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 300)
    public static void follow_NightMockPlayerOutsideSettlement_CrossesBoundary(GameTestHelper helper) {
        MovementScene.prepare(helper);
        SpiritEntity spirit = MovementScene.spirit(helper, SPIRIT);
        spirit.setHome(MovementScene.coord(helper, HOME));
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, FOLLOW_PLAYER, FOLLOW_PLAYER_NAME);
        GameTestFixtures.holdDayTime(helper, NIGHT);
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall("follow", Map.of("target", FOLLOW_PLAYER_NAME, "radius", 3)),
                MovementScene.SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            double fromHome = MovementScene.distanceTo(helper, spirit, HOME);
            helper.assertTrue(
                    fromHome > SETTLEMENT_RADIUS,
                    "следование за игроком — исключение: дух обязан перейти границу поселения, расстояние от дома "
                            + fromHome + "; игрок " + player.blockPosition() + " (от духа "
                            + spirit.distanceTo(player) + "), статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет") + ", причина "
                            + spirit.taskFailureReason().orElse("нет") + ", решение "
                            + spirit.tactics().explain().orElse("нет") + ", сбоев тактики "
                            + spirit.tactics().failures() + " "
                            + spirit.tactics().lastFailure().orElse("нет")
                            + "; " + MovementScene.facts(helper, spirit, NIGHT));
            MovementScene.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 300)
    public static void come_NightMockPlayerOutsideSettlement_Arrives(GameTestHelper helper) {
        MovementScene.prepare(helper);
        SpiritEntity spirit = MovementScene.spirit(helper, SPIRIT);
        spirit.setHome(MovementScene.coord(helper, HOME));
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, FOLLOW_PLAYER, COME_PLAYER_NAME);
        GameTestFixtures.holdDayTime(helper, NIGHT);
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall("come_to_player", Map.of("player", COME_PLAYER_NAME)),
                MovementScene.SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            String playerFact = "; игрок " + player.blockPosition() + " (от духа "
                    + spirit.distanceTo(player) + "), тик духа " + spirit.tickCount
                    + ", тиков задачи " + run.spiritTicks()
                    + ", тик игры " + helper.getLevel().getGameTime();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "подход к игроку — исключение ночного поводка: come_to_player обязан дойти, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет") + ", причина "
                            + spirit.taskFailureReason().orElse("нет") + playerFact + "; "
                            + MovementScene.facts(helper, spirit, NIGHT));
            double toPlayer = MovementScene.distanceTo(helper, spirit, FOLLOW_PLAYER);
            helper.assertTrue(
                    toPlayer <= 2.0,
                    "дух обязан дойти к игроку за границей поселения, расстояние до игрока " + toPlayer + playerFact
                            + "; " + MovementScene.facts(helper, spirit, NIGHT));
            MovementScene.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 200)
    public static void chase_NightEnemyOutsideSettlement_StopsAtBoundary(GameTestHelper helper) {
        MovementScene.prepare(helper);
        SpiritEntity spirit = MovementScene.spirit(helper, SPIRIT);
        spirit.setHome(MovementScene.coord(helper, HOME));
        GameTestFixtures.holdDayTime(helper, NIGHT);
        Zombie enemy = helper.spawn(EntityType.ZOMBIE, OUTSIDE);
        enemy.setNoAi(true);
        enemy.setPersistenceRequired();
        AtomicReference<Approach> approach = new AtomicReference<>();
        AtomicReference<String> refusal = new AtomicReference<>();

        helper.onEachTick(() -> {
            if (spirit.tickCount < MovementScene.SETTLE_TICKS) {
                return;
            }
            Approach current = approach.updateAndGet(previous -> previous == null ? new Approach(spirit) : previous);
            if (!current.isActive()) {
                current.begin(enemy, CombatActuator.MELEE_ARRIVAL);
            }
            current.tick();
            if (current.refusal() != null) {
                refusal.set(current.refusal());
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    spirit.tickCount >= MovementScene.SETTLE_TICKS + OBSERVE_TICKS,
                    "наблюдение за погоней ещё идёт: тик духа " + spirit.tickCount);
            helper.assertTrue(
                    refusal.get() != null && refusal.get().contains("ночь: за границу поселения не выходим"),
                    "погоня за врагом за границей поселения обязана остановиться с причиной, фактически: "
                            + refusal.get() + "; " + MovementScene.facts(helper, spirit, NIGHT));
            double toEnemy = MovementScene.distanceTo(helper, spirit, OUTSIDE);
            helper.assertTrue(
                    toEnemy > CombatActuator.MELEE_ARRIVAL, "погоня не переступает границу: до врага " + toEnemy);
            double fromHome = MovementScene.distanceTo(helper, spirit, HOME);
            helper.assertTrue(
                    fromHome <= SETTLEMENT_RADIUS, "дух остался внутри поселения, расстояние от дома " + fromHome);
            MovementScene.finish(helper);
        });
    }
}
