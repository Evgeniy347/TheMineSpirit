package com.theminespirit.neoforge.tactics;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.coordinator.HelpCall;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.PolicyExemptions;
import com.theminespirit.core.policy.PolicyId;
import com.theminespirit.core.tactics.CombatDirective;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.DirectiveOwner;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.RetreatMethod;
import com.theminespirit.core.tactics.SafePointChoice;
import com.theminespirit.core.tactics.Situation;
import com.theminespirit.core.tactics.TacticalOption;
import com.theminespirit.core.tactics.TacticalOutcome;
import com.theminespirit.core.tactics.TacticsAction;
import com.theminespirit.core.tactics.TacticsConfig;
import com.theminespirit.core.tactics.TacticsEngine;
import com.theminespirit.core.tactics.TacticsMemory;
import com.theminespirit.core.tactics.adapter.CoreFactCollector;
import com.theminespirit.core.tactics.adapter.DecisionOrder;
import com.theminespirit.core.tactics.adapter.DecisionRecord;
import com.theminespirit.core.tactics.adapter.ExecutionResult;
import com.theminespirit.core.tactics.adapter.ForcedRetreat;
import com.theminespirit.core.tactics.adapter.IntentActionExecutor;
import com.theminespirit.core.tactics.adapter.RetreatCommitment;
import com.theminespirit.core.tactics.adapter.RetreatPlan;
import com.theminespirit.core.tactics.adapter.RetreatPlanner;
import com.theminespirit.core.tactics.adapter.RetreatTracker;
import com.theminespirit.core.tactics.adapter.StoredTactics;
import com.theminespirit.core.tactics.adapter.TacticsAdapterSettings;
import com.theminespirit.core.tactics.adapter.TacticsSchedule;
import com.theminespirit.core.tactics.adapter.TacticsTrace;
import com.theminespirit.core.tactics.adapter.TargetDesignation;
import com.theminespirit.neoforge.Approach;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

public final class TacticsService {
    public static final CombatMode DEFAULT_MODE = TacticsOrders.DEFAULT;
    public static final double RETURN_ARRIVAL = 1.5;
    public static final int FAILURE_BACKOFF_TICKS = 100;
    public static final int DECISIONS_IN_CHAT = 5;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final TacticsEngine ENGINE = new TacticsEngine(TacticsConfig.loadDefault());
    private static final int NEVER = -1_000_000;

    final SpiritEntity spirit;
    private final TacticsAdapterSettings settings = TacticsAdapterSettings.loadDefault();
    private final WorldFactCollector collector = new WorldFactCollector(settings);
    private final CoreFactCollector situations = new CoreFactCollector(settings);
    private final IntentActionExecutor intents = new IntentActionExecutor();
    private final TacticsSchedule schedule = new TacticsSchedule(settings.evalIntervalTicks());
    private final TacticsRally rally;
    private final Approach approach;
    private final CombatActuator combat;
    private final RetreatExecutor retreating;

    private final TacticsOrders orders;
    private final TacticsMetrics metrics;

    private TacticsMemory memory;
    private RetreatTracker retreat = RetreatTracker.IDLE;
    private TacticalOutcome outcome;
    private ExecutionResult result = ExecutionResult.idle();
    private WorldFactCollector.Collected facts;
    private Optional<RetreatPlan> plan = Optional.empty();
    private RetreatMethod planMethod = RetreatMethod.NONE;
    private TacticsAction action = TacticsAction.IDLE;
    private final TacticsReturn returnWalk;
    private String targetId;
    private String shootTargetId;
    private boolean active;
    private boolean paused;
    private boolean forced;
    private boolean hurtPending;
    private int lastHurtTick = NEVER;
    private long suspendedUntil = Long.MIN_VALUE;
    private RetreatExecutor.Progress progress = RetreatExecutor.Progress.DONE;

    public TacticsService(SpiritEntity spirit) {
        this.spirit = spirit;
        this.orders = new TacticsOrders(this);
        this.metrics = new TacticsMetrics(settings.journalCapacity(), settings.journalMaxPerSecond());
        this.rally = new TacticsRally(spirit);
        this.approach = new Approach(spirit);
        this.combat = new CombatActuator(spirit, approach);
        this.retreating = new RetreatExecutor(spirit, approach, combat, collector);
        this.returnWalk = new TacticsReturn(this, spirit, approach, retreating);
    }

    public void tick() {
        Level level = spirit.level();
        if (level.isClientSide()) {
            return;
        }
        long now = level.getGameTime();
        if (now < suspendedUntil) {
            return;
        }
        if (paused) {
            paused = false;
            forced = true;
        }
        try {
            if (schedule.due(now, hurtPending, forced)) {
                boolean byEvent = hurtPending;
                hurtPending = false;
                forced = false;
                evaluate(now, byEvent);
            }
            execute();
        } catch (RuntimeException error) {
            metrics.failed(error);
            suspendedUntil = now + FAILURE_BACKOFF_TICKS;
            release();
            LOGGER.error(
                    "Дух {} не провёл тактику и приостановил её на {} тиков: {}",
                    spirit.getUUID(),
                    FAILURE_BACKOFF_TICKS,
                    error.getMessage(),
                    error);
        }
    }

    public void onHurt(DamageSource source) {
        hurtPending = true;
        lastHurtTick = spirit.tickCount;
    }

    public void pause() {
        if (!paused) {
            paused = true;
            retreating.reset();
            combat.stop();
        }
    }

    public void setCombatMode(CombatMode mode) {
        orders.setMode(mode);
    }

    public CombatMode combatMode() {
        return orders.mode();
    }

    public void setGuardZone(BlockCoord center, double radius) {
        orders.zone(center, radius);
    }

    public void clearGuardZone() {
        orders.clearZone();
    }

    public void setWard(UUID id) {
        orders.ward(id);
    }

    public void applyDirective(CombatDirective directive) {
        orders.applyDirective(directive, DirectiveOwner.SCRIPT);
    }

    public void applyDirective(CombatDirective directive, DirectiveOwner owner) {
        orders.applyDirective(directive, owner);
    }

    public boolean clearDirective() {
        return orders.clearDirective(DirectiveOwner.SCRIPT);
    }

    public boolean clearDirective(DirectiveOwner owner) {
        return orders.clearDirective(owner);
    }

    public Optional<CombatDirective> activeDirective() {
        return orders.directives().active();
    }

    public void holdDirective() {
        orders.holdDirective();
    }

    public boolean directiveHeld() {
        return orders.directives().held();
    }

    public void setForcedTarget(LivingEntity target) {
        orders.setForcedTarget(target);
    }

    void setOrdered(CombatMode mode) {
        orders.restoreMode(mode);
    }

    void markForced() {
        forced = true;
    }

    public boolean hasState() {
        return memory != null || orders.hasState() || retreat.retreating();
    }

    public boolean wantsControl() {
        return active;
    }

    public TacticsAction action() {
        return active ? action : TacticsAction.IDLE;
    }

    public PolicyExemptions exemptions() {
        return active && retreat.method() == RetreatMethod.WATER
                ? PolicyExemptions.of(PolicyId.WATER)
                : PolicyExemptions.empty();
    }

    public Optional<TacticalOutcome> lastOutcome() {
        return Optional.ofNullable(outcome);
    }

    public RetreatTracker retreat() {
        return retreat;
    }

    public RetreatExecutor.Progress progress() {
        return progress;
    }

    public CombatActuator combat() {
        return combat;
    }

    public RetreatExecutor retreatExecutor() {
        return retreating;
    }

    public int stepCalls() {
        return metrics.stepCalls();
    }

    public int blockReads() {
        return metrics.blockReads();
    }

    public int entityQueries() {
        return metrics.entityQueries();
    }

    public int failures() {
        return metrics.failures();
    }

    public Optional<String> lastFailure() {
        return metrics.lastFailure();
    }

    public Optional<String> restoreFailure() {
        return metrics.restoreFailure();
    }

    public Optional<String> explain() {
        return Optional.ofNullable(outcome).map(TacticsTrace::format);
    }

    public List<DecisionRecord> decisions(int limit) {
        return metrics.decisions(limit);
    }

    public Optional<HelpCall> helpCall() {
        return rally.call();
    }

    public int helpCalls() {
        return rally.calls();
    }

    public StoredTactics snapshot() {
        return new StoredTactics(
                orders.mode(), orders.zone(), orders.ward().map(UUID::toString), Optional.ofNullable(memory), retreat);
    }

    public void restore(StoredTactics stored) {
        orders.restoreMode(stored.ordered());
        orders.zone(stored.zone());
        orders.ward(stored.ward().map(UUID::fromString).orElse(null));
        memory = stored.memory().orElse(null);
        retreat = stored.retreat();
        orders.directives().resetHeld();
        forced = true;
    }

    public void failRestore(String reason) {
        metrics.failRestore(reason);
    }

    private void evaluate(long now, boolean byEvent) {
        TacticsInputs inputs = new TacticsInputs(
                orders.mode(),
                orders.zone(),
                orders.ward(),
                ticksSince(lastHurtTick),
                ticksSince(combat.lastStrikeTick()),
                designatedTarget());
        WorldFactCollector.Collected collected = collector.collect(spirit, inputs);
        metrics.step(collected.blockReads(), collected.entityQueries());
        Situation situation = situations.collect(collected.context());
        if (memory == null) {
            memory = TacticsMemory.initial(orders.mode(), now, situation.self().hpFraction());
        }
        TacticalOutcome next = ENGINE.step(situation, memory, now);
        schedule.evaluated(now, byEvent);
        memory = next.memory();
        next = ForcedRetreat.apply(
                next, orders.directives().current(), orders.directives().method());
        retreat = RetreatCommitment.next(retreat, next, now, settings);
        result = intents.execute(next, collected.context());
        Optional<EnemyFact> designated = TargetDesignation.pick(next, collected.context());
        Optional<LivingEntity> forcedTarget = orders.directives().livingTarget();
        if (forcedTarget.isPresent()) {
            targetId = designated.map(EnemyFact::id).orElse(null);
            spirit.setTarget(forcedTarget.get());
        } else {
            boolean engages = engages(designated);
            targetId = engages ? designated.map(EnemyFact::id).orElse(null) : null;
            spirit.setTarget(
                    engages
                            ? designated
                                    .map(enemy -> collected.entities().get(enemy.id()))
                                    .orElse(null)
                            : null);
        }
        shootTargetId = collected.context().enemies().stream()
                .filter(EnemyFact::visible)
                .min(Comparator.comparingDouble(
                        enemy -> enemy.pos().distanceTo(situation.self().pos())))
                .map(EnemyFact::id)
                .orElse(null);
        replan(collected, situation);
        returnWalk.set(next.returnTo().flatMap(point -> ReturnAnchor.stand(spirit, point)));
        logChange(next);
        journalDecision(now, next, situation);
        rally.update(now, next, situation);
        outcome = next;
        facts = collected;
    }

    private void journalDecision(long now, TacticalOutcome next, Situation situation) {
        DecisionOrder order = DecisionOrder.of(
                orders.mode(), orders.zone().isPresent(), orders.ward().map(UUID::toString), activeDirective());
        metrics.journalDecision(
                now, spirit.getUUID().toString(), spirit.displayName(), next, targetId, order, situation);
    }

    private Optional<LivingEntity> designatedTarget() {
        return orders.directives().livingTarget();
    }

    private boolean engages(Optional<EnemyFact> designated) {
        if (orders.mode() == CombatMode.PASSIVE) {
            return false;
        }
        if (TacticsConfig.loadDefault().modes().of(orders.mode()).initiates()) {
            return true;
        }
        return spirit.getLastHurtByMob() != null
                || designated.map(EnemyFact::targetingSelf).orElse(false);
    }

    private void replan(WorldFactCollector.Collected collected, Situation situation) {
        if (!retreat.retreating()) {
            plan = Optional.empty();
            planMethod = RetreatMethod.NONE;
            return;
        }
        boolean stale = plan.isEmpty() || planMethod != retreat.method() || plan.get() instanceof RetreatPlan.Walk;
        if (stale) {
            plan = planFor(collected, situation);
            planMethod = retreat.method();
        }
    }

    private Optional<RetreatPlan> planFor(WorldFactCollector.Collected collected, Situation situation) {
        BlockPos feet = spirit.blockPosition();
        Optional<Point> retreatPoint = orders.directives().point().isPresent()
                ? orders.directives().point()
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
        return RetreatPlanner.plan(retreat.method(), input);
    }

    private void execute() {
        if (outcome == null) {
            return;
        }
        if (retreat.retreating() && plan.isPresent()) {
            active = true;
            action = TacticsAction.RETREAT;
            progress = retreating.step(plan.get(), entity(shootTargetId));
            return;
        }
        LivingEntity target = orders.directives().target() != null
                        && orders.directives().target().isAlive()
                ? orders.directives().target()
                : entity(targetId).orElse(null);
        if (outcome.decision() == TacticalOption.FIGHT && target != null) {
            active = true;
            action = TacticsAction.FIGHT;
            returnWalk.clearCooldown();
            retreating.reset();
            if (result instanceof ExecutionResult.Shooting && spirit.gear().arrows() > 0) {
                combat.shoot(target);
            } else {
                combat.melee(target);
            }
            return;
        }
        if (outcome.decision() == TacticalOption.IGNORE
                && !orders.directives().held()
                && returnWalk.due(spirit.level().getGameTime())) {
            active = true;
            action = orders.zone().isPresent() ? TacticsAction.GUARD : TacticsAction.RETURN;
            returnWalk.walk(returnWalk.point());
            return;
        }
        release();
    }

    void release() {
        action = TacticsAction.IDLE;
        if (active) {
            retreating.reset();
            combat.stop();
            spirit.gear().holdNothing();
            spirit.setTarget(null);
        }
        active = false;
    }

    private Optional<LivingEntity> entity(String id) {
        if (id == null || facts == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(facts.entities().get(id)).filter(LivingEntity::isAlive);
    }

    private int ticksSince(int tick) {
        return (int) Math.min(Situation.MAX_TICKS, Math.max(0L, (long) spirit.tickCount - tick));
    }

    private void logChange(TacticalOutcome next) {
        if (outcome == null
                || outcome.decision() != next.decision()
                || outcome.retreatMethod() != next.retreatMethod()) {
            LOGGER.debug("Дух {}: {}", spirit.getUUID(), TacticsTrace.format(next));
        }
    }
}
