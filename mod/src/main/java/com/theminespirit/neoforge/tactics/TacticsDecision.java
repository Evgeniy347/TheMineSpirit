package com.theminespirit.neoforge.tactics;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.RetreatMethod;
import com.theminespirit.core.tactics.SafePointChoice;
import com.theminespirit.core.tactics.Situation;
import com.theminespirit.core.tactics.TacticalOutcome;
import com.theminespirit.core.tactics.TacticsConfig;
import com.theminespirit.core.tactics.TacticsEngine;
import com.theminespirit.core.tactics.TacticsMemory;
import com.theminespirit.core.tactics.adapter.DecisionOrder;
import com.theminespirit.core.tactics.adapter.ForcedRetreat;
import com.theminespirit.core.tactics.adapter.RetreatCommitment;
import com.theminespirit.core.tactics.adapter.RetreatPlan;
import com.theminespirit.core.tactics.adapter.RetreatPlanner;
import com.theminespirit.core.tactics.adapter.TargetDesignation;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;

final class TacticsDecision {
    private static final TacticsEngine ENGINE = new TacticsEngine(TacticsConfig.loadDefault());

    private final TacticsService host;

    TacticsDecision(TacticsService host) {
        this.host = host;
    }

    void evaluate(long now, boolean byEvent) {
        TacticsInputs inputs = new TacticsInputs(
                host.orders.mode(),
                host.orders.zone(),
                host.orders.ward(),
                ticksSince(host.lastHurtTick),
                ticksSince(host.combat.lastStrikeTick()),
                designatedTarget());
        WorldFactCollector.Collected collected = host.collector.collect(host.spirit, inputs);
        host.metrics.step(collected.blockReads(), collected.entityQueries());
        Situation situation = host.situations.collect(collected.context());
        if (host.memory == null) {
            host.memory = TacticsMemory.initial(
                    host.orders.mode(), now, situation.self().hpFraction());
        }
        TacticalOutcome next = ENGINE.step(situation, host.memory, now);
        host.schedule.evaluated(now, byEvent);
        host.memory = next.memory();
        next = ForcedRetreat.apply(
                next,
                host.orders.directives().current(),
                host.orders.directives().method());
        host.retreat = RetreatCommitment.next(host.retreat, next, now, host.settings);
        host.result = host.intents.execute(next, collected.context());
        Optional<EnemyFact> designated = TargetDesignation.pick(next, collected.context());
        Optional<LivingEntity> forcedTarget = host.orders.directives().livingTarget();
        if (forcedTarget.isPresent()) {
            host.targetId = designated.map(EnemyFact::id).orElse(null);
            host.spirit.setTarget(forcedTarget.get());
        } else {
            boolean engages = engages(designated);
            host.targetId = engages ? designated.map(EnemyFact::id).orElse(null) : null;
            host.spirit.setTarget(
                    engages
                            ? designated
                                    .map(enemy -> collected.entities().get(enemy.id()))
                                    .orElse(null)
                            : null);
        }
        host.shootTargetId = collected.context().enemies().stream()
                .filter(EnemyFact::visible)
                .min(Comparator.comparingDouble(
                        enemy -> enemy.pos().distanceTo(situation.self().pos())))
                .map(EnemyFact::id)
                .orElse(null);
        replan(collected, situation);
        host.returnWalk.set(next.returnTo().flatMap(point -> ReturnAnchor.stand(host.spirit, point)));
        host.logChange(next);
        journalDecision(now, next, situation);
        host.rally.update(now, next, situation);
        host.outcome = next;
        host.facts = collected;
    }

    private void journalDecision(long now, TacticalOutcome next, Situation situation) {
        DecisionOrder order = DecisionOrder.of(
                host.orders.mode(),
                host.orders.zone().isPresent(),
                host.orders.ward().map(UUID::toString),
                host.activeDirective());
        host.metrics.journalDecision(
                now,
                host.spirit.getUUID().toString(),
                host.spirit.displayName(),
                next,
                host.targetId,
                order,
                situation);
    }

    private Optional<LivingEntity> designatedTarget() {
        return host.orders.directives().livingTarget();
    }

    private boolean engages(Optional<EnemyFact> designated) {
        if (host.orders.mode() == CombatMode.PASSIVE) {
            return false;
        }
        if (TacticsConfig.loadDefault().modes().of(host.orders.mode()).initiates()) {
            return true;
        }
        return host.spirit.getLastHurtByMob() != null
                || designated.map(EnemyFact::targetingSelf).orElse(false);
    }

    private void replan(WorldFactCollector.Collected collected, Situation situation) {
        if (!host.retreat.retreating()) {
            host.plan = Optional.empty();
            host.planMethod = RetreatMethod.NONE;
            return;
        }
        boolean stale = host.plan.isEmpty()
                || host.planMethod != host.retreat.method()
                || host.plan.get() instanceof RetreatPlan.Walk;
        if (stale) {
            host.plan = planFor(collected, situation);
            host.planMethod = host.retreat.method();
        }
    }

    private int ticksSince(int tick) {
        return (int) Math.min(Situation.MAX_TICKS, Math.max(0L, (long) host.spirit.tickCount - tick));
    }

    private Optional<RetreatPlan> planFor(WorldFactCollector.Collected collected, Situation situation) {
        BlockPos feet = host.spirit.blockPosition();
        Optional<Point> retreatPoint = host.orders.directives().point().isPresent()
                ? host.orders.directives().point()
                : SafePointChoice.nearest(situation.self().pos(), collected.home(), collected.camp());
        RetreatPlanner.Input input = new RetreatPlanner.Input(
                situation.self().pos(),
                new BlockCoord(feet.getX(), feet.getY(), feet.getZ()),
                collected.context().enemies(),
                collected.env(),
                retreatPoint,
                situation.self().blocks(),
                collected.rally(),
                situation.self().hasWaterBucket());
        return RetreatPlanner.plan(host.retreat.method(), input);
    }
}
