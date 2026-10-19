package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.policy.PolicyVerdict;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.neoforge.SpiritBrain;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TacticsPolicyGameTests {
    private static final int TOUGH_HUSK_HEALTH = 200;
    private static final int WAIT_TICKS = 3000;
    private static final int QUIET_TICKS = 10;
    private static final int RESUME_LIMIT_TICKS = 14;
    private static final double CREEPER_HOLD = 7.0;
    private static final int FLEE_LIMIT_TICKS = 80;
    private static final int OVERRIDE_TICKS_FOR_PROOF = 5;

    private TacticsPolicyGameTests() {}

    @GameTest(template = "arena", batch = "tactics_policy_lava", timeoutTicks = 400)
    public static void lava_BesideFightingSpirit_TacticsFrozenTaskRunningAndTacticsResumesAfterTenQuietTicks(
            GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = spawnWaitingSpiritFacingToughHusk(helper);
        boolean[] lavaPlaced = {false};
        int[] frozenSteps = {-1};
        int[] overriddenTicks = {0};
        int[] lastReflexTick = {-1};
        int[] resumedStepAt = {-1};
        String[] tacticsReason = {null};
        String[] policyReason = {null};
        StringBuilder history = new StringBuilder();
        String[] lastState = {""};
        helper.onEachTick(() -> {
            SpiritBrain brain = spirit.brainIfPresent().orElse(null);
            if (brain == null) {
                return;
            }
            if (spirit.policies().verdict() instanceof PolicyVerdict.Reflex) {
                lastReflexTick[0] = spirit.tickCount;
            }
            appendStateChange(spirit, history, lastState);
            if (!lavaPlaced[0] && spirit.tactics().combat().strikes() >= 1) {
                BlockPos beside = BlockPos.containing(helper.relativeVec(spirit.position()))
                        .offset(0, -1, -1);
                helper.setBlock(beside, Blocks.LAVA);
                lavaPlaced[0] = true;
                tacticsReason[0] = brain.suspendReason().orElse(null);
            }
            if (spirit.policies().isOverriding()) {
                overriddenTicks[0]++;
                policyReason[0] = brain.suspendReason().orElse(null);
                if (frozenSteps[0] < 0) {
                    frozenSteps[0] = spirit.tactics().stepCalls();
                }
                assertTacticsFrozenTaskRunning(helper, spirit, brain, frozenSteps);
            } else if (frozenSteps[0] >= 0
                    && resumedStepAt[0] < 0
                    && spirit.tactics().stepCalls() > frozenSteps[0]) {
                resumedStepAt[0] = spirit.tickCount;
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(lavaPlaced[0], "бой ещё не начался: ударов нет");
            helper.assertTrue(
                    overriddenTicks[0] >= OVERRIDE_TICKS_FOR_PROOF,
                    "лава рядом обязана передать тик политикам хотя бы на " + OVERRIDE_TICKS_FOR_PROOF + " тиков: "
                            + overriddenTicks[0]);
            helper.assertTrue(
                    tacticsReason[0] != null && tacticsReason[0].startsWith("тактика"),
                    "в бою задачу приостановила тактика: причина «" + tacticsReason[0] + "»");
            helper.assertTrue(
                    policyReason[0] != null && policyReason[0].startsWith("LAVA_FIRE"),
                    "при рефлексе причина приостановки — политика LAVA_FIRE: «" + policyReason[0] + "»");
            assertTacticsResumed(helper, spirit, resumedStepAt, frozenSteps, history);
            helper.assertTrue(
                    resumedStepAt[0] - lastReflexTick[0] >= QUIET_TICKS
                            && resumedStepAt[0] - lastReflexTick[0] <= RESUME_LIMIT_TICKS,
                    "тактика возобновляется после " + QUIET_TICKS + " тихих тиков и сразу оценивает: рефлекс "
                            + lastReflexTick[0] + ", оценка " + resumedStepAt[0]);
            helper.assertTrue(!(spirit.policies().verdict() instanceof PolicyVerdict.Reflex), "рефлекс закончился");
            TacticsScene.finish(helper, spirit);
        });
    }

    private static SpiritEntity spawnWaitingSpiritFacingToughHusk(GameTestHelper helper) {
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));
        spirit.getInventory().addItem(new ItemStack(Items.IRON_SWORD));
        spirit.tactics().setCombatMode(CombatMode.DEFENSIVE);
        spirit.startTask(new ScriptCall("wait", Map.of("ticks", WAIT_TICKS)));
        Husk husk = TacticsScene.watcher(helper, EntityType.HUSK, new BlockPos(7, 1, 5));
        husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(TOUGH_HUSK_HEALTH);
        husk.setHealth(TOUGH_HUSK_HEALTH);
        husk.setTarget(spirit);
        return spirit;
    }

    private static void appendStateChange(SpiritEntity spirit, StringBuilder history, String[] lastState) {
        String state = spirit.policies().owner() + "/"
                + spirit.policies().verdict().getClass().getSimpleName() + "/ctl="
                + spirit.tactics().wantsControl();
        if (!state.equals(lastState[0])) {
            history.append(spirit.tickCount).append(':').append(state).append(' ');
            lastState[0] = state;
        }
    }

    private static void assertTacticsFrozenTaskRunning(
            GameTestHelper helper, SpiritEntity spirit, SpiritBrain brain, int[] frozenSteps) {
        helper.assertTrue(
                spirit.tactics().stepCalls() == frozenSteps[0],
                "пока политика владеет тиком, TacticsEngine.step не зовут: было " + frozenSteps[0] + ", стало "
                        + spirit.tactics().stepCalls());
        helper.assertTrue(
                brain.isSuspended() && spirit.taskStatus().orElse(null) == RunStatus.RUNNING,
                "во время рефлекса задача приостановлена, но RUNNING: статус "
                        + spirit.taskStatus().orElse(null));
    }

    private static void assertTacticsResumed(
            GameTestHelper helper, SpiritEntity spirit, int[] resumedStepAt, int[] frozenSteps, StringBuilder history) {
        helper.assertTrue(
                resumedStepAt[0] >= 0,
                "тактика ещё не вернулась к оценке: владелец "
                        + spirit.policies().owner()
                        + ", рефлекс " + spirit.policies().verdict() + ", оценок "
                        + spirit.tactics().stepCalls() + " при замороженных "
                        + frozenSteps[0] + ", хочет управление "
                        + spirit.tactics().wantsControl() + ", сбоев "
                        + spirit.tactics().failures() + ", позиция " + helper.relativeVec(spirit.position())
                        + ", ударов "
                        + spirit.tactics().combat().strikes() + ", история " + history);
    }

    @GameTest(template = "arena", batch = "tactics_policy_explosive", timeoutTicks = 300)
    public static void creeper_FourBlocksAway_ExplosionReflexOwnsTickTacticsFrozenAndSpiritKeepsSeven(
            GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 3));
        spirit.getInventory().addItem(new ItemStack(Items.IRON_SWORD));
        Creeper creeper = TacticsScene.watcher(helper, EntityType.CREEPER, new BlockPos(5, 1, 7));
        int[] frozenSteps = {-1};
        int[] overriddenTicks = {0};
        helper.onEachTick(() -> {
            if (spirit.policies().isOverriding()) {
                overriddenTicks[0]++;
                if (frozenSteps[0] < 0) {
                    frozenSteps[0] = spirit.tactics().stepCalls();
                }
                helper.assertTrue(
                        spirit.tactics().stepCalls() == frozenSteps[0],
                        "пока рефлекс EXPLOSIVE владеет тиком, тактика не оценивает: было " + frozenSteps[0]
                                + ", стало " + spirit.tactics().stepCalls());
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(spirit.tickCount >= FLEE_LIMIT_TICKS, "наблюдение ещё идёт: " + spirit.tickCount);
            helper.assertTrue(
                    overriddenTicks[0] >= OVERRIDE_TICKS_FOR_PROOF,
                    "рефлекс отхода от крипера обязан владеть тиком: " + overriddenTicks[0] + " тиков");
            helper.assertTrue(
                    spirit.distanceTo(creeper) >= CREEPER_HOLD - 0.5,
                    "рефлекс держит дух не ближе 7 блоков от крипера, расстояние " + spirit.distanceTo(creeper));
            TacticsScene.finish(helper, spirit);
        });
    }
}
