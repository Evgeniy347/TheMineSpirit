package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MovementApproachGameTests {
    private static final BlockPos APPROACH_START = new BlockPos(1, 1, 4);
    private static final BlockPos DIAMOND = new BlockPos(6, 1, 4);
    private static final int APPROACH_LIMIT_TICKS = 200;
    private static final int NO_STAND_LIMIT_TICKS = 2;
    private static final double STANDS_STILL = 0.1;
    private static final int CHAMBER_MIN_X = 3;
    private static final int CHAMBER_MAX_X = 8;
    private static final int CHAMBER_MIN_Z = 1;
    private static final int CHAMBER_MAX_Z = 7;
    private static final int CHAMBER_TOP = 4;

    private MovementApproachGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 300)
    public static void approach_DiamondOnMoundBehindGlass_StandsOnMoundInReachAndLeavesBlockIntact(
            GameTestHelper helper) {
        MovementScene.prepare(helper);
        for (int x = 5; x <= 7; x++) {
            for (int z = 3; z <= 5; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        helper.setBlock(DIAMOND, Blocks.DIAMOND_BLOCK);
        for (int z = 3; z <= 5; z++) {
            helper.setBlock(new BlockPos(4, 1, z), Blocks.GLASS);
            helper.setBlock(new BlockPos(4, 2, z), Blocks.GLASS);
        }
        SpiritEntity spirit = MovementScene.spirit(helper, APPROACH_START);
        SpiritRun run = SpiritRun.launch(
                helper, spirit, MovementScene.pointCall(helper, "approach", DIAMOND), MovementScene.SETTLE_TICKS);
        helper.assertTrue(
                MovementScene.eyeDistanceTo(helper, spirit, DIAMOND) > MovementScene.REACH,
                "из точки старта алмазный блок дальше досягаемости, иначе подходить не к чему");

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "к блоку есть стоянка на холме: approach обязан закончиться успехом, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            double reach = MovementScene.eyeDistanceTo(helper, spirit, DIAMOND);
            helper.assertTrue(
                    reach <= MovementScene.REACH,
                    "дух обязан встать в досягаемости " + MovementScene.REACH + " от блока, а до него " + reach);
            helper.assertTrue(
                    helper.relativeVec(spirit.position()).y >= 1.9,
                    "стоянка — на холме выше пола, а дух стоит на высоте " + helper.relativeVec(spirit.position()).y);
            helper.assertTrue(
                    helper.getBlockState(DIAMOND).is(Blocks.DIAMOND_BLOCK),
                    "approach только подходит: алмазный блок обязан остаться целым");
            helper.assertTrue(
                    run.spiritTicks() <= APPROACH_LIMIT_TICKS,
                    "подход обязан уложиться в " + APPROACH_LIMIT_TICKS + " тиков духа, ушло " + run.spiritTicks());
            MovementScene.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 100)
    public static void approach_TargetInSolidChamber_FailsWithNowhereToStandAndStands(GameTestHelper helper) {
        MovementScene.prepare(helper);
        for (int x = CHAMBER_MIN_X; x <= CHAMBER_MAX_X; x++) {
            for (int z = CHAMBER_MIN_Z; z <= CHAMBER_MAX_Z; z++) {
                for (int y = 1; y <= CHAMBER_TOP; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
        helper.setBlock(DIAMOND, Blocks.DIAMOND_BLOCK);
        SpiritEntity spirit = MovementScene.spirit(helper, APPROACH_START);
        SpiritRun run = SpiritRun.launch(
                helper, spirit, MovementScene.pointCall(helper, "approach", DIAMOND), MovementScene.SETTLE_TICKS);
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
                    "цель в глухой камере: approach обязан провалиться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет"));
            helper.assertTrue(
                    spirit.taskFailureReason().orElse("").contains("подойти некуда"),
                    "причина обязана говорить, что подойти некуда, фактически: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    run.spiritTicks() <= NO_STAND_LIMIT_TICKS,
                    "отказ идёт до первого шага, за " + NO_STAND_LIMIT_TICKS + " тика, а прошло " + run.spiritTicks());
            helper.assertTrue(
                    spirit.position().distanceTo(startAt[0]) < STANDS_STILL,
                    "без стоянки дух стоит, а он сдвинулся на "
                            + spirit.position().distanceTo(startAt[0]));
            helper.assertTrue(
                    helper.getBlockState(DIAMOND).is(Blocks.DIAMOND_BLOCK),
                    "approach не ломает блоков: алмазный блок обязан остаться целым");
            MovementScene.finish(helper);
        });
    }
}
