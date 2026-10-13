package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.tactics.CombatDirective;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.TargetSelector;
import com.theminespirit.core.tactics.TacticalOption;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.SpiritTargets;
import com.theminespirit.neoforge.tactics.SpiritGear;
import com.theminespirit.neoforge.tactics.TacticsService;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

    @GameTest(template = "arena", batch = "combat_attack", timeoutTicks = 400)
    public static void attack_HuskNearby_SpiritKillsTargetAndRestoresMode(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));
        spirit.getInventory().addItem(new ItemStack(Items.IRON_SWORD));
        Husk husk = TacticsScene.watcher(helper, EntityType.HUSK, new BlockPos(8, 1, 5));
        CombatMode before = spirit.tactics().combatMode();
        Optional<LivingEntity> sighted = SpiritTargets.resolve(spirit, TargetSelector.parse("hostile"));
        helper.assertTrue(sighted.isPresent(),
                "хуск обязан найтись ДО старта задачи, иначе провал скрипта скажет ложь: дух "
                        + helper.relativeVec(spirit.position()) + ", хуск " + helper.relativeVec(husk.position())
                        + ", здоровье " + husk.getHealth());

        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("attack", Map.of()), 10);
        int[] lastStrikeTick = {-1};
        int[] minGap = {Integer.MAX_VALUE};
        int[] strikesSeen = {0};
        helper.onEachTick(() -> {
            int strikes = spirit.tactics().combat().strikes();
            if (strikes > strikesSeen[0]) {
                if (lastStrikeTick[0] >= 0) {
                    minGap[0] = Math.min(minGap[0], spirit.tickCount - lastStrikeTick[0]);
                }
                lastStrikeTick[0] = spirit.tickCount;
                strikesSeen[0] = strikes;
            }
        });

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "attack обязан убить цель и завершиться успехом, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(husk.isDeadOrDying() || husk.isRemoved(),
                    "цель attack обязана быть убита, здоровье хуска: " + husk.getHealth());
            helper.assertTrue(strikesSeen[0] >= 2,
                    "для убийства нужно несколько ударов, ударов: " + strikesSeen[0]);
            helper.assertTrue(minGap[0] >= SpiritGear.ATTACK_COOLDOWN_TICKS,
                    "удары обязаны идти не чаще SpiritGear.ATTACK_COOLDOWN_TICKS ("
                            + SpiritGear.ATTACK_COOLDOWN_TICKS + "), минимальный интервал: " + minGap[0]);
            helper.assertTrue(spirit.tactics().combatMode() == before,
                    "после SUCCEEDED приказ обязан сняться и вернуть прежний режим, режим: "
                            + spirit.tactics().combatMode() + " вместо " + before);
            TacticsScene.finish(helper, spirit);
        });
    }

    @GameTest(template = "arena", batch = "combat_defend", timeoutTicks = 400)
    public static void defend_WardUnderThreat_SpiritKillsAggressorAndWardSurvives(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(3, 1, 5));
        spirit.getInventory().addItem(new ItemStack(Items.IRON_SWORD));
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(5, 1, 5), "Warded1");
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(6, 1, 5));
        husk.setTarget(player);

        SpiritRun run = SpiritRun.launch(helper, spirit,
                new ScriptCall("defend", Map.of("target", "player:Warded1")), 10);
        boolean[] wardSeen = {false};
        helper.onEachTick(() -> wardSeen[0] |= spirit.tactics().snapshot().ward().isPresent());

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "defend обязан убрать угрозу и завершиться успехом, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(husk.isDeadOrDying() || husk.isRemoved(),
                    "угрожавший подопечному моб обязан быть убит, здоровье: " + husk.getHealth());
            helper.assertTrue(player.isAlive() && !player.isRemoved(),
                    "подопечный обязан выжить под защитой, здоровье: " + player.getHealth());
            helper.assertTrue(wardSeen[0],
                    "подопечный обязан быть задан тактике (setWard) хотя бы один тик: без этого защитник "
                            + "не знает, кого защищать");
            TacticsScene.finish(helper, spirit);
        });
    }

    @GameTest(template = "arena", batch = "combat_defend_missing", timeoutTicks = 150)
    public static void defend_WardMissing_FailsWithReason(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));

        SpiritRun run = SpiritRun.launch(helper, spirit,
                new ScriptCall("defend", Map.of("target", "player:Nobody404")), 10);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "defend без подопечного обязан отказаться, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(reason.contains("не найден"),
                    "отказ обязан называть, что подопечный не найден, причина: " + reason);
            TacticsScene.finish(helper, spirit);
        });
    }
}
