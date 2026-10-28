package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.script.AttackParams;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.TargetSelector;
import com.theminespirit.neoforge.ScriptTargetResolver;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.combat.AttackScript;
import com.theminespirit.neoforge.tactics.SpiritGear;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
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
final class CombatAttackGameTests {
    private CombatAttackGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void attack_HuskNearby_SpiritKillsTargetAndRestoresMode(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));
        spirit.getInventory().addItem(new ItemStack(Items.IRON_SWORD));
        Husk husk = TacticsScene.watcher(helper, EntityType.HUSK, new BlockPos(8, 1, 5));
        CombatMode before = spirit.tactics().combatMode();
        Optional<LivingEntity> sighted = ScriptTargetResolver.resolve(spirit, TargetSelector.parse("hostile"));
        helper.assertTrue(
                sighted.isPresent(),
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
                huskSighted[0] |= ScriptTargetResolver.resolve(spirit, TargetSelector.parse("hostile"))
                        .isPresent();
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
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "attack обязан убить цель и завершиться успехом, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет") + "; хуск виден тиками: " + huskSighted[0]
                            + ", ближайшая дистанция: " + closestSeen[0] + ", здоровье[0..4]: "
                            + Arrays.toString(huskHealthAt) + ", цель духа: " + spirit.getTarget());
            helper.assertTrue(
                    husk.isDeadOrDying() || husk.isRemoved(),
                    "цель attack обязана быть убита, здоровье хуска: " + husk.getHealth());
            helper.assertTrue(strikesSeen[0] >= 2, "для убийства нужно несколько ударов, ударов: " + strikesSeen[0]);
            helper.assertTrue(
                    minGap[0] >= SpiritGear.ATTACK_COOLDOWN_TICKS,
                    "удары обязаны идти не чаще SpiritGear.ATTACK_COOLDOWN_TICKS (" + SpiritGear.ATTACK_COOLDOWN_TICKS
                            + "), минимальный интервал: " + minGap[0]);
            helper.assertTrue(
                    spirit.tactics().combatMode() == before,
                    "после SUCCEEDED приказ обязан сняться и вернуть прежний режим, режим: "
                            + spirit.tactics().combatMode() + " вместо " + before);
            helper.assertTrue(
                    spirit.taskResult().orElse(null) instanceof ScriptResult.Data data
                            && "AGGRESSIVE".equals(data.fields().get("mode"))
                            && "hostile".equals(data.fields().get("target"))
                            && "target_down".equals(data.fields().get("outcome")),
                    "успешный attack обязан положить итог Data{mode=AGGRESSIVE, target=hostile, outcome=target_down}, "
                            + "получено "
                            + spirit.taskResult().map(Object::toString).orElse("пусто"));
            TacticsScene.finish(helper, spirit);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 150)
    public static void attack_TargetResolvedOnce_EntityQueriesDontGrowPerTick(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));
        Husk husk = TacticsScene.watcher(helper, EntityType.HUSK, new BlockPos(8, 1, 5));
        AttackScript script = new AttackScript(spirit, AttackParams.from(Map.of()));
        int base = spirit.entityQueries();

        ScriptStatus first = script.tick();
        helper.assertTrue(
                first == ScriptStatus.RUNNING,
                "первый тик атаки обязан продолжаться, статус: " + first
                        + (script.failureReason() != null ? ", причина: " + script.failureReason() : ""));
        int afterFirst = spirit.entityQueries() - base;
        helper.assertTrue(
                afterFirst == 1,
                "первый тик обязан ровно один раз разрешить цель, запросов: " + afterFirst + " вместо 1");
        for (int i = 0; i < 30; i++) {
            script.tick();
            helper.assertTrue(
                    !(husk.isDeadOrDying() || husk.isRemoved()),
                    "хуск не вправе погибнуть в этом тесте сам по себе, здоровье: " + husk.getHealth());
        }
        int grown = spirit.entityQueries() - base;
        helper.assertTrue(
                grown <= 1,
                "пока цель жива, скрипт не опрашивает мир повторно: запросов " + grown
                        + " за 31 тик, норма — не больше 1");
        script.cancel();
        TacticsScene.finish(helper, spirit);
        helper.succeed();
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void defend_WardUnderThreat_SpiritKillsAggressorAndWardSurvives(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(3, 1, 5));
        spirit.getInventory().addItem(new ItemStack(Items.IRON_SWORD));
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(5, 1, 5), "Warded1");
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(6, 1, 5));
        husk.setTarget(player);

        SpiritRun run =
                SpiritRun.launch(helper, spirit, new ScriptCall("defend", Map.of("target", "player:Warded1")), 10);
        boolean[] wardSeen = {false};
        helper.onEachTick(
                () -> wardSeen[0] |= spirit.tactics().snapshot().ward().isPresent());

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "defend обязан убрать угрозу и завершиться успехом, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    husk.isDeadOrDying() || husk.isRemoved(),
                    "угрожавший подопечному моб обязан быть убит, здоровье: " + husk.getHealth());
            helper.assertTrue(
                    player.isAlive() && !player.isRemoved(),
                    "подопечный обязан выжить под защитой, здоровье: " + player.getHealth());
            helper.assertTrue(
                    wardSeen[0],
                    "подопечный обязан быть задан тактике (setWard) хотя бы один тик: без этого защитник "
                            + "не знает, кого защищать");
            helper.assertTrue(
                    spirit.tactics().snapshot().ward().isEmpty(),
                    "после снятия приказа defend подопечный обязан выйти из ward, подопечный: "
                            + spirit.tactics().snapshot().ward().orElse("нет"));
            TacticsScene.finish(helper, spirit);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 150)
    public static void defend_WardMissing_FailsWithReason(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));

        SpiritRun run =
                SpiritRun.launch(helper, spirit, new ScriptCall("defend", Map.of("target", "player:Nobody404")), 10);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "defend без подопечного обязан отказаться, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(
                    reason.contains("не найден"),
                    "отказ обязан называть, что подопечный не найден, причина: " + reason);
            TacticsScene.finish(helper, spirit);
        });
    }
}
