package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.tactics.CombatDirective;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.TargetSelector;
import com.theminespirit.core.tactics.TacticalOption;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.SpiritTargets;
import com.theminespirit.neoforge.tactics.TacticsService;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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

    @GameTest(template = "arena", batch = "combat_target_player", timeoutTicks = 150)
    public static void targets_ResolvePlayerByName_ReturnsThatPlayer(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(5, 1, 3), "Steve42");

        Optional<LivingEntity> found = SpiritTargets.resolve(spirit, TargetSelector.parse("player:Steve42"));
        helper.assertTrue(found.isPresent(), "игрок по имени обязан находиться, игрок: " + player.getName().getString());
        helper.assertTrue(found.get() == player,
                "найден должен быть ровно тот игрок, что искали, найден: " + found.get());
        TacticsScene.finish(helper, spirit);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "combat_target_spirit", timeoutTicks = 150)
    public static void targets_ResolveSpiritUuid_ReturnsThatSpirit(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));
        SpiritEntity other = TacticsScene.spirit(helper, new BlockPos(5, 1, 7));

        Optional<LivingEntity> found = SpiritTargets.resolve(spirit,
                TargetSelector.parse("spirit:" + other.getUUID()));
        helper.assertTrue(found.isPresent(), "дух по UUID обязан находиться, UUID: " + other.getUUID());
        helper.assertTrue(found.get() == other,
                "найден должен быть ровно тот дух, что искали, найден: " + found.get());
        TacticsScene.finish(helper, spirit);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "combat_target_hostile", timeoutTicks = 150)
    public static void targets_ResolveHostileKind_ReturnsNearestMonster(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));
        Husk near = TacticsScene.watcher(helper, EntityType.HUSK, new BlockPos(6, 1, 6));
        Husk far = TacticsScene.watcher(helper, EntityType.HUSK, new BlockPos(5, 1, 10));

        Optional<LivingEntity> found = SpiritTargets.resolve(spirit, TargetSelector.parse("hostile"));
        helper.assertTrue(found.isPresent(), "враждебный моб обязан находиться, мобов: 2, найдено пусто");
        helper.assertTrue(found.get() == near,
                "ближайший враждебный обязан выбираться первым, найден: " + found.get()
                        + " вместо " + near + ", дальние: " + far);
        TacticsScene.finish(helper, spirit);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "combat_target_missing", timeoutTicks = 150)
    public static void targets_ResolveUnknownPlayer_ReturnsEmpty(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));

        Optional<LivingEntity> found = SpiritTargets.resolve(spirit, TargetSelector.parse("player:Nobody404"));
        helper.assertTrue(found.isEmpty(),
                "отсутствующий игрок обязан давать Optional.empty(), а не исключение, найден: " + found.orElse(null));
        TacticsScene.finish(helper, spirit);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "combat_target_empty", timeoutTicks = 150)
    public static void targets_ResolveWithoutTargets_ReturnsEmpty(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));

        Optional<LivingEntity> found = SpiritTargets.resolve(spirit, TargetSelector.parse("nearest"));
        helper.assertTrue(found.isEmpty(),
                "на пустой арене ближайший обязан давать Optional.empty(), найден: " + found.orElse(null));
        TacticsScene.finish(helper, spirit);
        helper.succeed();
    }

    @GameTest(template = "arena", batch = "combat_target_queries", timeoutTicks = 150)
    public static void targets_ResolveTwice_CountsTwoEntityQueries(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));
        int before = spirit.entityQueries();

        SpiritTargets.resolve(spirit, TargetSelector.parse("nearest"));
        SpiritTargets.resolve(spirit, TargetSelector.parse("hostile"));
        helper.assertTrue(spirit.entityQueries() == before + 2,
                "каждый resolve обязан ровно один раз запросить мир, запросов: "
                        + (spirit.entityQueries() - before) + " вместо 2");
        TacticsScene.finish(helper, spirit);
        helper.succeed();
    }
}
