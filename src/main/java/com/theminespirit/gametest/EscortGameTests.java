package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EscortGameTests {
    private static final int RADIUS = 2;
    private static final double HELD = 2.2;
    private static final String PLAYER = "gt_escort_ward";
    private static final int ARRIVAL_LIMIT_TICKS = 150;

    private EscortGameTests() {
    }

    @GameTest(template = "arena", batch = "escort_follow", timeoutTicks = 400)
    public static void escort_MovingPlayer_FollowsAndHoldsRadiusAtEachStop(GameTestHelper helper) {
        MovementScene.prepare(helper);
        SpiritEntity spirit = MovementScene.spirit(helper, new BlockPos(1, 1, 4));
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(5, 1, 4), PLAYER);
        SpiritRun run = SpiritRun.launch(helper, spirit,
                MovementScene.call("escort", Map.of("target", PLAYER, "radius", RADIUS)),
                MovementScene.SETTLE_TICKS);
        int[] firstArrival = {-1};
        int[] secondArrival = {-1};
        boolean[] moved = {false};

        helper.onEachTick(() -> {
            if (!run.started()) {
                return;
            }
            double distance = spirit.position().distanceTo(player.position());
            if (firstArrival[0] < 0) {
                if (distance <= HELD) {
                    firstArrival[0] = run.spiritTicks();
                } else if (run.spiritTicks() > ARRIVAL_LIMIT_TICKS) {
                    helper.fail("за " + ARRIVAL_LIMIT_TICKS + " тиков дух не подошёл к цели на " + HELD
                            + ": расстояние " + distance);
                }
                return;
            }
            if (!moved[0]) {
                moved[0] = true;
                BlockPos target = helper.absolutePos(new BlockPos(8, 1, 4));
                player.moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
                return;
            }
            if (secondArrival[0] < 0 && distance <= HELD) {
                secondArrival[0] = run.spiritTicks();
            } else if (secondArrival[0] < 0 && run.spiritTicks() > firstArrival[0] + ARRIVAL_LIMIT_TICKS) {
                helper.fail("после ухода цели дух не догнал её за " + ARRIVAL_LIMIT_TICKS + " тиков: расстояние "
                        + distance);
            }
        });

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(firstArrival[0] >= 0,
                    "escort обязан подойти к игроку и держать радиус " + RADIUS + ": первая стоянка не достигнута");
            helper.assertTrue(secondArrival[0] >= 0,
                    "escort обязан идти за движущейся целью: вторая стоянка не достигнута");
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.RUNNING,
                    "escort не заканчивается у цели, пока задачу не отменят: статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            MovementScene.removePlayer(helper, player);
            MovementScene.finish(helper);
        });
    }
}
