package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MovementClimbGameTests {
    private static final int WALL_X = 3;
    private static final int LADDER_X = 4;
    private static final int CELL_Z = 4;
    private static final int LADDER_HEIGHT = 6;
    private static final int CLIMB_BLOCKS = 5;
    private static final BlockPos BASE = new BlockPos(LADDER_X, 1, CELL_Z);
    private static final BlockPos TOP_PLATFORM = new BlockPos(5, 5, CELL_Z);
    private static final BlockPos TOP = new BlockPos(5, 6, CELL_Z);
    private static final int CLIMB_LIMIT_TICKS = 200;

    private MovementClimbGameTests() {
    }

    @GameTest(template = "arena", batch = "move_climb_up", timeoutTicks = 300)
    public static void climb_LadderFiveBlocksUp_ReachesTopRung(GameTestHelper helper) {
        MovementScene.prepare(helper);
        buildLadder(helper);
        SpiritEntity spirit = MovementScene.spirit(helper, BASE);
        SpiritRun run = SpiritRun.launch(helper, spirit, MovementScene.call("climb", Map.of()),
                MovementScene.SETTLE_TICKS);
        double[] peak = {relativeY(helper, spirit)};
        helper.onEachTick(() -> peak[0] = Math.max(peak[0], relativeY(helper, spirit)));

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "подъём по лестнице обязан закончиться успехом, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(peak[0] >= LADDER_HEIGHT,
                    "дух обязан подняться на " + CLIMB_BLOCKS + " блоков от y=" + BASE.getY() + " до верхней ступени y="
                            + LADDER_HEIGHT + ", а забрался только до y=" + peak[0]);
            helper.assertTrue(run.spiritTicks() <= CLIMB_LIMIT_TICKS,
                    "подъём на " + CLIMB_BLOCKS + " блоков обязан уложиться в " + CLIMB_LIMIT_TICKS + " тиков духа, "
                            + "ушло " + run.spiritTicks());
            MovementScene.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "move_climb_down", timeoutTicks = 400)
    public static void climb_LadderFiveBlocksDown_ReachesBottomFloor(GameTestHelper helper) {
        MovementScene.prepare(helper);
        buildLadder(helper);
        helper.setBlock(TOP_PLATFORM, Blocks.STONE);
        SpiritEntity spirit = MovementScene.spirit(helper, TOP);
        SpiritRun run = SpiritRun.launch(helper, spirit, MovementScene.call("climb", Map.of("down", true)),
                MovementScene.SETTLE_TICKS);
        double[] startY = {-1};
        helper.onEachTick(() -> {
            if (run.started() && startY[0] < 0) {
                startY[0] = relativeY(helper, spirit);
            }
        });

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "спуск по лестнице обязан закончиться успехом, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(startY[0] >= LADDER_HEIGHT - 0.5,
                    "спуск обязан начаться с верхней площадки y=" + LADDER_HEIGHT + ", а дух стартовал с y=" + startY[0]);
            double feetY = relativeY(helper, spirit);
            helper.assertTrue(feetY <= BASE.getY() + 0.5,
                    "дух обязан спуститься к низу колонны y=" + BASE.getY() + ", а оказался на y=" + feetY);
            helper.assertTrue(helper.relativePos(spirit.blockPosition()).getY() <= BASE.getY(),
                    "дух обязан стоять на нижней ступени, а его клетка y="
                            + helper.relativePos(spirit.blockPosition()).getY());
            MovementScene.finish(helper);
        });
    }

    private static double relativeY(GameTestHelper helper, SpiritEntity spirit) {
        return helper.relativeVec(spirit.position()).y;
    }

    private static void buildLadder(GameTestHelper helper) {
        BlockPos ladder = new BlockPos(LADDER_X, 1, CELL_Z);
        for (int y = 1; y <= LADDER_HEIGHT; y++) {
            helper.setBlock(new BlockPos(WALL_X, y, CELL_Z), Blocks.STONE);
        }
        for (int y = 1; y <= LADDER_HEIGHT; y++) {
            helper.setBlock(ladder.offset(0, y - 1, 0),
                    Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST));
        }
    }
}
