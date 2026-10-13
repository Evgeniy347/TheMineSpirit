package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.tactics.CombatDirective;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.TargetSelector;
import com.theminespirit.core.tactics.TacticalOption;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.tactics.TacticsService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CombatGameTests {
    private static final int OBSERVED_TICKS = 40;

    private CombatGameTests() {
    }

    @GameTest(template = "arena", batch = "combat_directive", timeoutTicks = 300)
    public static void directive_ApplyThenClear_RestoresModeAndKeepsTactics(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));
        TacticsService tactics = spirit.tactics();
        CombatMode before = tactics.combatMode();
        Husk husk = TacticsScene.watcher(helper, EntityType.HUSK, new BlockPos(5, 1, 9));

        tactics.applyDirective(CombatDirective.attack(TargetSelector.parse("hostile")));
        helper.assertTrue(tactics.combatMode() == CombatMode.AGGRESSIVE,
                "приказ обязан задать режим, режим после attack-приказа: " + tactics.combatMode());
        helper.assertTrue(tactics.activeDirective().isPresent(),
                "активный приказ обязан быть виден скрипту после applyDirective");
        tactics.setForcedTarget(husk);
        helper.assertTrue(spirit.getTarget() == husk,
                "принудительная цель обязана встать духу, цель: " + spirit.getTarget());

        tactics.clearDirective();
        helper.assertTrue(tactics.combatMode() == before,
                "снятие обязано вернуть прежний режим, режим: " + tactics.combatMode() + " вместо " + before);
        helper.assertTrue(tactics.activeDirective().isEmpty(),
                "после снятия приказа активный приказ обязан исчезнуть");
        helper.assertTrue(spirit.getTarget() == null,
                "снятие обязано убрать назначенную скриптом цель, цель: " + spirit.getTarget());

        int[] ticks = {0};
        helper.onEachTick(() -> ticks[0]++);
        helper.succeedWhen(() -> {
            helper.assertTrue(ticks[0] >= OBSERVED_TICKS,
                    "тактике после снятия дают поработать не меньше " + OBSERVED_TICKS + " тиков, прошло: " + ticks[0]);
            helper.assertTrue(tactics.failures() == 0,
                    "снятие приказа не роняет тактику, failures: " + tactics.failures()
                            + ", причина: " + tactics.lastFailure().orElse("не указана"));
            helper.assertTrue(tactics.lastOutcome().isPresent(),
                    "тактика продолжает давать решения после снятия приказа");
            TacticsScene.finish(helper, spirit);
        });
    }

    @GameTest(template = "arena", batch = "combat_flee", timeoutTicks = 150)
    public static void directive_FleeForcesRetreat_EvenWithoutEnemies(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));
        TacticsService tactics = spirit.tactics();
        tactics.applyDirective(CombatDirective.flee());
        helper.assertTrue(tactics.combatMode() == CombatMode.PASSIVE,
                "flee-приказ обязан задать PASSIVE, режим: " + tactics.combatMode());

        helper.succeedWhen(() -> {
            helper.assertTrue(tactics.lastOutcome().isPresent(),
                    "тактика обязана дать решение после flee-приказа");
            TacticalOption decision = tactics.lastOutcome().orElseThrow().decision();
            helper.assertTrue(decision == TacticalOption.RETREAT,
                    "flee-приказ обязан принудительно включить отход даже без врагов, решение: " + decision);
            helper.assertTrue(spirit.getTarget() == null,
                    "flee не назначает цель, цель: " + spirit.getTarget());
            TacticsScene.finish(helper, spirit);
        });
    }
}
