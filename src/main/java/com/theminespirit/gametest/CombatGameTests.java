package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.tactics.CombatDirective;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.CombatTuning;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.TargetSelector;
import com.theminespirit.core.tactics.TacticalOption;
import com.theminespirit.core.tactics.adapter.RetreatPlanner;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.SpiritTargets;
import com.theminespirit.neoforge.tactics.SpiritGear;
import com.theminespirit.neoforge.tactics.TacticsService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
import net.minecraft.world.level.block.Blocks;
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

    @GameTest(template = "arena", batch = "combat_directive_flee", timeoutTicks = 150)
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

        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("attack", Map.of()), 0);
        int[] lastStrikeTick = {-1};
        int[] minGap = {Integer.MAX_VALUE};
        int[] strikesSeen = {0};
        boolean[] huskSighted = {false};
        double[] closestSeen = {Double.MAX_VALUE};
        int[] huskHealthAt = {20, 20, 20, 20, 20};
        helper.onEachTick(() -> {
            int t = (int) helper.getTick();
            if (t < huskHealthAt.length) {
                huskHealthAt[t] = (int) husk.getHealth();
            }
            if (!husk.isDeadOrDying() && !husk.isRemoved()) {
                double distance = new Point(spirit.getX(), spirit.getY(), spirit.getZ())
                        .distanceTo(new Point(husk.getX(), husk.getY(), husk.getZ()));
                closestSeen[0] = Math.min(closestSeen[0], distance);
                huskSighted[0] |= SpiritTargets.resolve(spirit, TargetSelector.parse("hostile")).isPresent();
            }
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
                            + spirit.taskFailureReason().orElse("нет") + "; хуск виден тиками: " + huskSighted[0]
                            + ", ближайшая дистанция: " + closestSeen[0] + ", здоровье[0..4]: "
                            + Arrays.toString(huskHealthAt) + ", цель духа: " + spirit.getTarget());
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

    @GameTest(template = "arena", batch = "combat_flee", timeoutTicks = 400)
    public static void flee_HuskClose_SpiritEscapesWithoutFighting(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));
        spirit.getInventory().addItem(new ItemStack(Items.IRON_SWORD));
        Husk husk = TacticsScene.watcher(helper, EntityType.HUSK, new BlockPos(8, 1, 5));
        for (int x = -15; x <= 0; x++) {
            for (int z = 3; z <= 7; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.COBBLESTONE);
            }
        }

        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("flee", Map.of()), 0);
        double startDistance = new Point(spirit.getX(), spirit.getY(), spirit.getZ())
                .distanceTo(new Point(husk.getX(), husk.getY(), husk.getZ()));
        double[] closestSeen = {Double.MAX_VALUE};
        int[] huskHealthAt = {20, 20, 20, 20, 20};
        helper.onEachTick(() -> {
            int t = (int) helper.getTick();
            if (t < huskHealthAt.length) {
                huskHealthAt[t] = (int) husk.getHealth();
            }
            if (!husk.isDeadOrDying() && !husk.isRemoved()) {
                double distance = new Point(spirit.getX(), spirit.getY(), spirit.getZ())
                        .distanceTo(new Point(husk.getX(), husk.getY(), husk.getZ()));
                closestSeen[0] = Math.min(closestSeen[0], distance);
            }
        });

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "flee обязан увести духа из опасности и завершиться успехом, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет") + "; старт dist=" + startDistance
                            + ", min dist=" + closestSeen[0] + ", здоровье[0..4]: "
                            + Arrays.toString(huskHealthAt));
            double distance = new Point(spirit.getX(), spirit.getY(), spirit.getZ())
                    .distanceTo(new Point(husk.getX(), husk.getY(), husk.getZ()));
            helper.assertTrue(distance > CombatTuning.DANGER_RADIUS,
                    "дух обязан убежать дальше CombatTuning.DANGER_RADIUS ("
                            + CombatTuning.DANGER_RADIUS + "), осталось: " + distance);
            helper.assertTrue(spirit.tactics().combat().strikes() == 0,
                    "flee — бегство, а не бой: ударов обязано быть ноль, ударов: "
                            + spirit.tactics().combat().strikes());
            TacticsScene.finish(helper, spirit);
        });
    }

    // [флак] тайм-аут скрипта FLEE_SAFE/FLEE_TIMEOUT_TICKS=600 тиков: ловушка честно ждёт отказ по тайм-ауту
    @GameTest(template = "arena", batch = "combat_flee_trap", timeoutTicks = 900)
    public static void flee_TrappedInWalls_TimesOutWithTickCount(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));
        for (int y = 1; y <= 2; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    helper.setBlock(new BlockPos(5 + dx, y, 5 + dz), Blocks.COBBLESTONE);
                }
            }
        }
        TacticsScene.watcher(helper, EntityType.HUSK, new BlockPos(7, 1, 5));

        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("flee", Map.of()), 0);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "в ловушке flee обязан отказаться по тайм-ауту, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(reason.contains("тайм-аут " + CatalogLimits.FLEE_TIMEOUT_TICKS),
                    "отказ обязан называть тайм-аут " + CatalogLimits.FLEE_TIMEOUT_TICKS
                            + " тиков, причина: " + reason);
            TacticsScene.finish(helper, spirit);
        });
    }

    @GameTest(template = "arena", batch = "combat_retreat", timeoutTicks = 400)
    public static void retreat_HomeSet_SpiritReturnsToHome(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(1, 1, 9));
        BlockPos absoluteHome = helper.absolutePos(new BlockPos(9, 1, 1));
        spirit.setHome(new BlockCoord(
                absoluteHome.getX(), absoluteHome.getY(), absoluteHome.getZ()));
        Point home = new Point(absoluteHome.getX() + 0.5, absoluteHome.getY(), absoluteHome.getZ() + 0.5);

        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("retreat", Map.of()), 10);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "retreat обязан довести духа до дома и завершиться успехом, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            double distance = new Point(spirit.getX(), spirit.getY(), spirit.getZ()).distanceTo(home);
            helper.assertTrue(distance <= RetreatPlanner.HOME_ARRIVAL + 0.25,
                    "дух обязан остановиться у дома (RetreatPlanner.HOME_ARRIVAL="
                            + RetreatPlanner.HOME_ARRIVAL + "), расстояние: " + distance);
            TacticsScene.finish(helper, spirit);
        });
    }

    @GameTest(template = "arena", batch = "combat_retreat_missing", timeoutTicks = 100)
    public static void retreat_NoHome_FailsWithNoPoint(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));

        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("retreat", Map.of()), 10);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "retreat без дома обязан отказаться, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(reason.contains("точки отхода нет"),
                    "отказ обязан называть, что точки отхода нет, причина: " + reason);
            TacticsScene.finish(helper, spirit);
        });
    }

    @GameTest(template = "arena", batch = "combat_scale", timeoutTicks = 400)
    public static void scale_TwentySpiritsAttackOneHusk_QueriesStayWithinLimits(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        List<SpiritEntity> spirits = new ArrayList<>();
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 5; column++) {
                spirits.add(TacticsScene.spirit(helper, new BlockPos(column + 1, 1, row + 1)));
            }
        }
        helper.assertTrue(spirits.size() == 20, "в тесте на масштаб 20 духов, создано " + spirits.size());
        TacticsScene.watcher(helper, EntityType.HUSK, new BlockPos(9, 1, 9));

        for (SpiritEntity spirit : spirits) {
            spirit.startTask(new ScriptCall("attack", Map.of()));
        }

        int observedTicks = 200;
        int evalInterval = 10;
        int maxEntityQueries = 20 * observedTicks / evalInterval;
        int maxBlockReads = 20 * observedTicks / evalInterval * 64;
        int maxSteps = 20 * observedTicks / evalInterval + 20;

        helper.succeedWhen(() -> {
            int reads = 0;
            int queries = 0;
            int steps = 0;
            for (SpiritEntity spirit : spirits) {
                helper.assertTrue(spirit.tickCount >= observedTicks,
                        "дух ещё не отработал " + observedTicks + " тиков: " + spirit.tickCount);
                reads += spirit.tactics().blockReads();
                queries += spirit.tactics().entityQueries();
                steps += spirit.tactics().stepCalls();
            }
            helper.assertTrue(steps > 0 && reads > 0 && queries > 0,
                    "тактика обязана работать, иначе предел ничего не доказывает: шагов " + steps
                            + ", чтений " + reads);
            helper.assertTrue(queries <= maxEntityQueries,
                    "запросов сущностей не больше " + maxEntityQueries + ", было " + queries);
            helper.assertTrue(reads <= maxBlockReads,
                    "чтений блоков не больше " + maxBlockReads + ", было " + reads);
            helper.assertTrue(steps <= maxSteps,
                    "шагов тактики не больше " + maxSteps + ", было " + steps);
            for (SpiritEntity spirit : spirits) {
                spirit.discard();
            }
            GameTestFixtures.reset(helper);
        });
    }
}
