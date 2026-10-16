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
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
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
    private static final double STANDS_STILL = 0.1;
    private static final String PLAYER_NAME = "gt_night_leash_player";

    private NightLeashGameTests() {
    }

    @GameTest(template = "arena", batch = "night_leash_refuse", timeoutTicks = 100)
    public static void goto_NightTargetOutsideSettlement_FailsWithReasonWithoutMoving(GameTestHelper helper) {
        MovementScene.prepare(helper);
        SpiritEntity spirit = MovementScene.spirit(helper, SPIRIT);
        spirit.setHome(MovementScene.coord(helper, HOME));
        GameTestFixtures.holdDayTime(helper, NIGHT);
        Vec3 start = helper.absoluteVec(Vec3.atBottomCenterOf(SPIRIT));
        SpiritRun run = SpiritRun.launch(helper, spirit, MovementScene.pointCall(helper, "goto", OUTSIDE),
                MovementScene.SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "ночью цель за границей поселения обязана быть отвергнута, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(reason.contains("ночь: за границу поселения не выходим"),
                    "причина обязана говорить про ночь и границу поселения, фактически: " + reason);
            helper.assertTrue(spirit.position().distanceTo(start) < STANDS_STILL,
                    "отказ приходит до первого шага, а дух сдвинулся на " + spirit.position().distanceTo(start));
            MovementScene.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "night_leash_allow", timeoutTicks = 200)
    public static void goto_DayTargetOutsideSettlement_Arrives(GameTestHelper helper) {
        MovementScene.prepare(helper);
        SpiritEntity spirit = MovementScene.spirit(helper, SPIRIT);
        spirit.setHome(MovementScene.coord(helper, HOME));
        GameTestFixtures.holdDayTime(helper, NOON);
        SpiritRun run = SpiritRun.launch(helper, spirit, MovementScene.pointCall(helper, "goto", OUTSIDE),
                MovementScene.SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "днём граница поселения не держит: goto за радиус обязан дойти, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            double distance = MovementScene.distanceTo(helper, spirit, OUTSIDE);
            helper.assertTrue(distance <= MovementScene.ARRIVAL,
                    "дух обязан встать у цели в пределах допуска " + MovementScene.ARRIVAL + ", расстояние " + distance);
            MovementScene.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "night_leash_follow", timeoutTicks = 300)
    public static void follow_NightMockPlayerOutsideSettlement_CrossesBoundary(GameTestHelper helper) {
        MovementScene.prepare(helper);
        SpiritEntity spirit = MovementScene.spirit(helper, SPIRIT);
        spirit.setHome(MovementScene.coord(helper, HOME));
        GameTestFixtures.mockPlayer(helper, FOLLOW_PLAYER, PLAYER_NAME);
        GameTestFixtures.holdDayTime(helper, NIGHT);
        SpiritRun run = SpiritRun.launch(helper, spirit,
                new ScriptCall("follow", Map.of("target", PLAYER_NAME, "radius", 3)), MovementScene.SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            double fromHome = MovementScene.distanceTo(helper, spirit, HOME);
            helper.assertTrue(fromHome > SETTLEMENT_RADIUS,
                    "следование за игроком — исключение: дух обязан перейти границу поселения, расстояние от дома "
                            + fromHome);
            MovementScene.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "night_leash_come", timeoutTicks = 300)
    public static void come_NightMockPlayerOutsideSettlement_Arrives(GameTestHelper helper) {
        MovementScene.prepare(helper);
        SpiritEntity spirit = MovementScene.spirit(helper, SPIRIT);
        spirit.setHome(MovementScene.coord(helper, HOME));
        GameTestFixtures.mockPlayer(helper, FOLLOW_PLAYER, PLAYER_NAME);
        GameTestFixtures.holdDayTime(helper, NIGHT);
        SpiritRun run = SpiritRun.launch(helper, spirit,
                new ScriptCall("come_to_player", Map.of("player", PLAYER_NAME)), MovementScene.SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "подход к игроку — исключение ночного поводка: come_to_player обязан дойти, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            double toPlayer = MovementScene.distanceTo(helper, spirit, FOLLOW_PLAYER);
            helper.assertTrue(toPlayer <= 2.0,
                    "дух обязан дойти к игроку за границей поселения, расстояние до игрока " + toPlayer);
            MovementScene.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "night_leash_chase", timeoutTicks = 200)
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
        Vec3[] start = new Vec3[1];

        helper.onEachTick(() -> {
            if (spirit.tickCount < MovementScene.SETTLE_TICKS) {
                return;
            }
            if (start[0] == null) {
                start[0] = spirit.position();
            }
            Approach current = approach.updateAndGet(previous -> previous == null
                    ? new Approach(spirit) : previous);
            if (!current.isActive()) {
                current.begin(enemy, CombatActuator.MELEE_ARRIVAL);
            }
            current.tick();
            if (current.refusal() != null) {
                refusal.set(current.refusal());
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(spirit.tickCount >= MovementScene.SETTLE_TICKS + OBSERVE_TICKS,
                    "наблюдение за погоней ещё идёт: тик духа " + spirit.tickCount);
            helper.assertTrue(refusal.get() != null && refusal.get().contains("ночь: за границу поселения не выходим"),
                    "погоня за врагом за границей поселения обязана остановиться с причиной, фактически: "
                            + refusal.get());
            helper.assertTrue(spirit.position().distanceTo(start[0]) < STANDS_STILL,
                    "дух не переступает границу поселения, а сдвинулся на "
                            + spirit.position().distanceTo(start[0]));
            MovementScene.finish(helper);
        });
    }
}
