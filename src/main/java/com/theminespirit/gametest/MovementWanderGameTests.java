package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MovementWanderGameTests {
    private static final BlockPos CENTER = new BlockPos(4, 1, 4);
    private static final int WANDER_TICKS = 200;
    private static final int WANDER_RADIUS = 4;
    private static final double WANDER_MIN_DISTANCE = 3.0;
    private static final int WANDER_END_SLACK_TICKS = 15;

    private MovementWanderGameTests() {
    }

    @GameTest(template = "arena", batch = "move_wander", timeoutTicks = 400)
    public static void wander_TwoHundredTicksRadiusFour_MovesAwayStaysOnFloorAndSucceedsAtExpiry(
            GameTestHelper helper) {
        MovementScene.prepare(helper);
        SpiritEntity spirit = MovementScene.spirit(helper, CENTER);
        SpiritRun run = SpiritRun.launch(helper, spirit,
                MovementScene.call("wander", Map.of("radius", WANDER_RADIUS, "duration_ticks", WANDER_TICKS)),
                MovementScene.SETTLE_TICKS);
        Vec3[] startAt = new Vec3[1];
        double[] farthest = {0};
        helper.onEachTick(() -> {
            if (spirit.tickCount < MovementScene.SETTLE_TICKS) {
                return;
            }
            if (startAt[0] == null) {
                startAt[0] = spirit.position();
            }
            farthest[0] = Math.max(farthest[0], spirit.position().distanceTo(startAt[0]));
            Vec3 relative = helper.relativeVec(spirit.position());
            if (relative.x < 0 || relative.x > GameTestFixtures.FLOOR_SIZE || relative.z < 0
                    || relative.z > GameTestFixtures.FLOOR_SIZE) {
                helper.fail("дух покинул площадку: позиция " + relative);
            }
        });

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "wander обязан закончиться успехом по истечении времени, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(run.spiritTicks() >= WANDER_TICKS && run.spiritTicks() <= WANDER_TICKS + WANDER_END_SLACK_TICKS,
                    "успех приходит по истечении " + WANDER_TICKS + " тиков, а пришёл на " + run.spiritTicks());
            helper.assertTrue(farthest[0] >= WANDER_MIN_DISTANCE,
                    "за " + WANDER_TICKS + " тиков дух обязан отойти от старта минимум на " + WANDER_MIN_DISTANCE
                            + " блока, максимум отхода " + farthest[0]);
            MovementScene.finish(helper);
        });
    }
}
