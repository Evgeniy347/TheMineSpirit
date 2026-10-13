package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MovementStepGameTests {
    private static final BlockPos STEP_START = new BlockPos(2, 1, 4);
    private static final BlockPos STEP_BLOCK = new BlockPos(3, 1, 4);
    private static final BlockPos STEP_CELL = new BlockPos(3, 2, 4);
    private static final int STEP_LIMIT_TICKS = 40;
    private static final int STEP_REFUSAL_LIMIT_TICKS = 2;
    private static final double STANDS_STILL = 0.1;
    private static final double STEP_TOLERANCE = 0.6;

    private MovementStepGameTests() {
    }

    @GameTest(template = "arena", batch = "move_step_up", timeoutTicks = 100)
    public static void stepOver_OneBlockStepAhead_StandsOnItWithinFortyTicks(GameTestHelper helper) {
        MovementScene.prepare(helper);
        helper.setBlock(STEP_BLOCK, Blocks.STONE);
        SpiritEntity spirit = MovementScene.spirit(helper, STEP_START);
        SpiritRun run = SpiritRun.launch(helper, spirit, MovementScene.call("step_over", Map.of("dx", 1, "dz", 0)),
                MovementScene.SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "ступенька на 1 блок вверх проходима: step_over обязан закончиться успехом, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            double distance = spirit.position().distanceTo(Vec3.atBottomCenterOf(helper.absolutePos(STEP_CELL)));
            helper.assertTrue(distance <= STEP_TOLERANCE,
                    "дух обязан стоять на ступеньке (центр клетки " + STEP_CELL + "), а до неё " + distance);
            helper.assertTrue(run.spiritTicks() <= STEP_LIMIT_TICKS,
                    "один шаг обязан уложиться в " + STEP_LIMIT_TICKS + " тиков духа, ушло " + run.spiritTicks());
            MovementScene.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "move_step_wall", timeoutTicks = 100)
    public static void stepOver_TwoBlockWallAhead_FailsAtOnceWithoutMoving(GameTestHelper helper) {
        MovementScene.prepare(helper);
        helper.setBlock(STEP_BLOCK, Blocks.STONE);
        helper.setBlock(STEP_CELL, Blocks.STONE);
        SpiritEntity spirit = MovementScene.spirit(helper, STEP_START);
        SpiritRun run = SpiritRun.launch(helper, spirit, MovementScene.call("step_over", Map.of("dx", 1, "dz", 0)),
                MovementScene.SETTLE_TICKS);
        Vec3[] startAt = new Vec3[1];
        helper.onEachTick(() -> {
            if (startAt[0] == null && spirit.tickCount >= MovementScene.SETTLE_TICKS) {
                startAt[0] = spirit.position();
            }
        });

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "стена в 2 блока выше шага: step_over обязан провалиться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет"));
            helper.assertTrue(run.spiritTicks() <= STEP_REFUSAL_LIMIT_TICKS,
                    "отказ идёт до первого шага, за " + STEP_REFUSAL_LIMIT_TICKS + " тика, а прошло "
                            + run.spiritTicks());
            helper.assertTrue(spirit.position().distanceTo(startAt[0]) < STANDS_STILL,
                    "при отказе дух стоит, а он сдвинулся на " + spirit.position().distanceTo(startAt[0]));
            helper.assertTrue(spirit.pathBuilds() == 0, "при отказе путь не строится, построено " + spirit.pathBuilds());
            MovementScene.finish(helper);
        });
    }
}
