package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.HarvestSession;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritBrain;
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
public final class PolicySuspendGameTests {
    private static final int WALK_BEFORE_SUSPEND = 25;
    private static final int HOLD_TICKS = 30;
    private static final double MAX_DRIFT = 0.35;
    private static final BlockPos START = new BlockPos(1, 1, 4);
    private static final BlockPos TARGET = new BlockPos(8, 1, 4);
    private static final BlockPos DIRT = new BlockPos(6, 1, 4);

    private PolicySuspendGameTests() {
    }

    @GameTest(template = "arena", batch = "suspend_goto", timeoutTicks = 400)
    public static void suspend_GotoMidWalk_SpiritStandsStillThenResumeArrives(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), START);
        BlockPos absolute = helper.absolutePos(TARGET);
        SpiritRun run = SpiritRun.launch(helper, spirit,
                new ScriptCall("goto", Map.of("x", absolute.getX(), "y", absolute.getY(), "z", absolute.getZ())), 1);
        int[] suspendedAt = {-1};
        Vec3[] held = {null};
        helper.onEachTick(() -> {
            SpiritBrain brain = spirit.brainIfPresent().orElse(null);
            if (brain == null || run.spiritTicks() < 0) {
                return;
            }
            if (suspendedAt[0] < 0 && run.spiritTicks() == WALK_BEFORE_SUSPEND) {
                brain.suspend("тест: приостановка посреди пути");
                suspendedAt[0] = spirit.tickCount;
            }
            if (suspendedAt[0] >= 0 && spirit.tickCount == suspendedAt[0] + 5) {
                held[0] = spirit.position();
            }
            if (held[0] != null && spirit.tickCount < suspendedAt[0] + HOLD_TICKS) {
                helper.assertTrue(spirit.position().distanceTo(held[0]) < MAX_DRIFT,
                        "приостановленный дух обязан стоять: ушёл на " + spirit.position().distanceTo(held[0]));
                helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.RUNNING && brain.isSuspended(),
                        "приостановка не отменяет и не завершает задачу: статус " + spirit.taskStatus().map(RunStatus::name).orElse("нет"));
            }
            if (suspendedAt[0] >= 0 && spirit.tickCount == suspendedAt[0] + HOLD_TICKS) {
                brain.resume();
            }
        });

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(suspendedAt[0] >= 0 && spirit.tickCount > suspendedAt[0] + HOLD_TICKS,
                    "приостановка ещё не отработала: тик духа " + spirit.tickCount);
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "после возобновления дух обязан дойти до цели: статус " + spirit.taskStatus().map(RunStatus::name).orElse("нет")
                            + ", причина " + spirit.taskFailureReason().orElse("нет"));
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "arena", batch = "suspend_break", timeoutTicks = 500)
    public static void suspend_MidBreak_CrackIsRemovedBlockStaysThenBreakFinishes(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(DIRT, Blocks.DIRT);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(3, 1, 4));
        HarvestSession session = HarvestSession.gather(spirit, "minecraft:dirt", 1, 6);
        int[] phase = {0};
        int[] heldSince = {0};
        helper.onEachTick(() -> {
            if (spirit.tickCount < 2) {
                return;
            }
            switch (phase[0]) {
                case 0 -> {
                    session.tick();
                    if (session.crackStage() >= 2) {
                        helper.assertTrue(helper.getBlockState(DIRT).is(Blocks.DIRT), "блок в начале ломки обязан быть цел");
                        session.suspend();
                        helper.assertTrue(session.crackStage() == -1, "приостановка обязана снять трещины, стадия " + session.crackStage());
                        heldSince[0] = spirit.tickCount;
                        phase[0] = 1;
                    }
                }
                case 1 -> {
                    helper.assertTrue(helper.getBlockState(DIRT).is(Blocks.DIRT),
                            "пока ломка приостановлена, блок обязан стоять целым");
                    helper.assertTrue(session.crackStage() == -1, "трещин на приостановленной ломке быть не должно");
                    if (spirit.tickCount >= heldSince[0] + HOLD_TICKS) {
                        phase[0] = 2;
                    }
                }
                default -> session.tick();
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(phase[0] == 2, "приостановка ещё не отработала, фаза " + phase[0]);
            helper.assertTrue(session.outcome() == HarvestSession.Outcome.SUCCEEDED,
                    "после возобновления ломка обязана завершиться: итог " + session.outcome() + ", причина " + session.failureReason());
            helper.assertTrue(helper.getBlockState(DIRT).isAir() && session.mined() == 1,
                    "после возобновления блок обязан быть сломан ровно один раз, добыто " + session.mined());
            GameTestFixtures.reset(helper);
        });
    }
}
