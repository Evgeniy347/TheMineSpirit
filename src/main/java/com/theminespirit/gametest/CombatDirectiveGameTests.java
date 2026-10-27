package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.tactics.CombatDirective;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.TacticalOption;
import com.theminespirit.core.tactics.TargetSelector;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.tactics.TacticsService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Husk;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
final class CombatDirectiveGameTests {
    private static final int OBSERVED_TICKS = 40;

    private CombatDirectiveGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 300)
    public static void directive_ApplyThenClear_RestoresModeAndKeepsTactics(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));
        TacticsService tactics = spirit.tactics();
        CombatMode before = tactics.combatMode();
        Husk husk = TacticsScene.watcher(helper, EntityType.HUSK, new BlockPos(5, 1, 9));

        tactics.applyDirective(CombatDirective.attack(TargetSelector.parse("hostile")));
        helper.assertTrue(
                tactics.combatMode() == CombatMode.AGGRESSIVE,
                "приказ обязан задать режим, режим после attack-приказа: " + tactics.combatMode());
        helper.assertTrue(
                tactics.activeDirective().isPresent(),
                "активный приказ обязан быть виден скрипту после applyDirective");
        tactics.setForcedTarget(husk);
        helper.assertTrue(
                husk.equals(spirit.getTarget()),
                "принудительная цель обязана встать духу, цель: " + spirit.getTarget());

        tactics.clearDirective();
        helper.assertTrue(
                tactics.combatMode() == before,
                "снятие обязано вернуть прежний режим, режим: " + tactics.combatMode() + " вместо " + before);
        helper.assertTrue(tactics.activeDirective().isEmpty(), "после снятия приказа активный приказ обязан исчезнуть");
        helper.assertTrue(
                spirit.getTarget() == null,
                "снятие обязано убрать назначенную скриптом цель, цель: " + spirit.getTarget());

        int[] ticks = {0};
        helper.onEachTick(() -> ticks[0]++);
        helper.succeedWhen(() -> {
            helper.assertTrue(
                    ticks[0] >= OBSERVED_TICKS,
                    "тактике после снятия дают поработать не меньше " + OBSERVED_TICKS + " тиков, прошло: " + ticks[0]);
            helper.assertTrue(
                    tactics.failures() == 0,
                    "снятие приказа не роняет тактику, failures: " + tactics.failures() + ", причина: "
                            + tactics.lastFailure().orElse("не указана"));
            helper.assertTrue(
                    tactics.lastOutcome().isPresent(), "тактика продолжает давать решения после снятия приказа");
            TacticsScene.finish(helper, spirit);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 150)
    public static void directive_FleeForcesRetreat_EvenWithoutEnemies(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));
        TacticsService tactics = spirit.tactics();
        tactics.applyDirective(CombatDirective.flee());
        helper.assertTrue(
                tactics.combatMode() == CombatMode.PASSIVE,
                "flee-приказ обязан задать PASSIVE, режим: " + tactics.combatMode());

        helper.succeedWhen(() -> {
            helper.assertTrue(tactics.lastOutcome().isPresent(), "тактика обязана дать решение после flee-приказа");
            TacticalOption decision = tactics.lastOutcome().orElseThrow().decision();
            helper.assertTrue(
                    decision == TacticalOption.RETREAT,
                    "flee-приказ обязан принудительно включить отход даже без врагов, решение: " + decision);
            helper.assertTrue(spirit.getTarget() == null, "flee не назначает цель, цель: " + spirit.getTarget());
            TacticsScene.finish(helper, spirit);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 200)
    public static void forcedAttack_PassiveTarget_SpiritStillStrikes(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));
        Husk husk = TacticsScene.watcher(helper, EntityType.HUSK, new BlockPos(8, 1, 5));
        TacticsService tactics = spirit.tactics();
        tactics.applyDirective(CombatDirective.attack(TargetSelector.parse("hostile")));
        tactics.setForcedTarget(husk);

        int[] huskHealthStart = {(int) husk.getHealth()};
        helper.succeedWhen(() -> {
            helper.assertTrue(
                    husk.getHealth() < huskHealthStart[0],
                    "приказ attack с живой целью обязан бить её тем же решением ядра тактики, "
                            + "здоровье хуска: " + husk.getHealth() + " из " + huskHealthStart[0]
                            + ", ударов: " + tactics.combat().strikes());
            TacticsScene.finish(helper, spirit);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 200)
    public static void forcedAttack_AnimalTarget_SpiritStillStrikes(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));
        Cow cow = TacticsScene.watcher(helper, EntityType.COW, new BlockPos(8, 1, 5));
        TacticsService tactics = spirit.tactics();
        tactics.applyDirective(CombatDirective.attack(TargetSelector.parse("animal")));
        tactics.setForcedTarget(cow);

        float cowHealthStart = cow.getHealth();
        boolean[] fought = {false};
        helper.onEachTick(() ->
                tactics.lastOutcome().ifPresent(outcome -> fought[0] |= outcome.decision() == TacticalOption.FIGHT));
        helper.succeedWhen(() -> {
            helper.assertTrue(
                    fought[0],
                    "принудительная цель обязана пройти через ядро тактики решением FIGHT, а не мимо него, "
                            + "решение: "
                            + tactics.lastOutcome()
                                    .map(outcome -> outcome.decision().toString())
                                    .orElse("нет решений")
                            + ", трасса: " + tactics.explain().orElse("нет"));
            helper.assertTrue(
                    cow.getHealth() < cowHealthStart,
                    "приказ attack на не-врага (животное) обязан бить цель, пока ядро тактики видит её в фактах, "
                            + "здоровье коровы: " + cow.getHealth() + " из " + cowHealthStart
                            + ", ударов: " + tactics.combat().strikes());
            TacticsScene.finish(helper, spirit);
        });
    }
}
