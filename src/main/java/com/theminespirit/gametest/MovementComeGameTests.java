package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MovementComeGameTests {
    private static final BlockPos START = new BlockPos(1, 1, 1);
    private static final BlockPos PLAYER_AT = new BlockPos(7, 1, 1);
    private static final int COME_LIMIT_TICKS = 200;
    private static final int NO_TARGET_LIMIT_TICKS = 2;
    private static final double STANDS_STILL = 0.1;

    private MovementComeGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 300)
    public static void comeToPlayer_NoNameAndMockPlayerSixBlocksAway_ArrivesWithinToleranceNoLaterThan200Ticks(
            GameTestHelper helper) {
        MovementScene.prepare(helper);
        SpiritEntity spirit = MovementScene.spirit(helper, START);
        ServerPlayer player = MovementScene.mockPlayer(helper, PLAYER_AT);
        SpiritRun run = SpiritRun.launch(
                helper, spirit, MovementScene.call("come_to_player", Map.of()), MovementScene.SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "игрок в 6 блоках достижим: come_to_player без имени обязан дойти до ближайшего игрока, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            double distance = spirit.position().distanceTo(player.position());
            helper.assertTrue(
                    distance <= MovementScene.ARRIVAL,
                    "дух обязан стоять рядом с игроком в пределах " + MovementScene.ARRIVAL + ", расстояние "
                            + distance);
            helper.assertTrue(
                    run.spiritTicks() <= COME_LIMIT_TICKS,
                    "шесть блоков до игрока обязаны пройтись за " + COME_LIMIT_TICKS + " тиков духа, ушло "
                            + run.spiritTicks());
            MovementScene.removePlayer(helper, player);
            MovementScene.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 100)
    public static void comeToPlayer_UnknownPlayerName_FailsBeforeFirstStepAndStands(GameTestHelper helper) {
        MovementScene.prepare(helper);
        SpiritEntity spirit = MovementScene.spirit(helper, START);
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                MovementScene.call("come_to_player", Map.of("player", "НетТакогоИгрока")),
                MovementScene.SETTLE_TICKS);
        Vec3[] startAt = new Vec3[1];
        helper.onEachTick(() -> {
            if (startAt[0] == null && spirit.tickCount >= MovementScene.SETTLE_TICKS) {
                startAt[0] = spirit.position();
            }
        });

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "игрока с таким именем нет: come_to_player обязан провалиться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет"));
            helper.assertTrue(
                    spirit.taskFailureReason().orElse("").contains("НетТакогоИгрока"),
                    "причина обязана называть искомое имя, фактически: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    run.spiritTicks() <= NO_TARGET_LIMIT_TICKS,
                    "отказ без игрока идёт до первого шага: за " + NO_TARGET_LIMIT_TICKS + " тика, прошло "
                            + run.spiritTicks());
            helper.assertTrue(
                    spirit.position().distanceTo(startAt[0]) < STANDS_STILL,
                    "без цели дух стоит, а он сдвинулся на " + spirit.position().distanceTo(startAt[0]));
            helper.assertTrue(
                    spirit.pathBuilds() == 0, "до первого шага путь не строится, а построено " + spirit.pathBuilds());
            MovementScene.finish(helper);
        });
    }
}
