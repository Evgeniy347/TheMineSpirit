package com.theminespirit.neoforge.tactics;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.coordinator.HelpCall;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.PolicyExemptions;
import com.theminespirit.core.policy.PolicyId;
import com.theminespirit.core.tactics.CombatDirective;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.DirectiveOwner;
import com.theminespirit.core.tactics.RetreatMethod;
import com.theminespirit.core.tactics.TacticalOutcome;
import com.theminespirit.core.tactics.TacticsAction;
import com.theminespirit.core.tactics.TacticsMemory;
import com.theminespirit.core.tactics.adapter.CoreFactCollector;
import com.theminespirit.core.tactics.adapter.DecisionRecord;
import com.theminespirit.core.tactics.adapter.ExecutionResult;
import com.theminespirit.core.tactics.adapter.IntentActionExecutor;
import com.theminespirit.core.tactics.adapter.RetreatPlan;
import com.theminespirit.core.tactics.adapter.RetreatTracker;
import com.theminespirit.core.tactics.adapter.StoredTactics;
import com.theminespirit.core.tactics.adapter.TacticsAdapterSettings;
import com.theminespirit.core.tactics.adapter.TacticsSchedule;
import com.theminespirit.core.tactics.adapter.TacticsTrace;
import com.theminespirit.neoforge.Approach;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
    final SpiritEntity spirit;
    final TacticsAdapterSettings settings = TacticsAdapterSettings.loadDefault();
    final WorldFactCollector collector = new WorldFactCollector(settings);
    final CoreFactCollector situations = new CoreFactCollector(settings);
    final IntentActionExecutor intents = new IntentActionExecutor();
    final TacticsSchedule schedule = new TacticsSchedule(settings.evalIntervalTicks());
    final TacticsRally rally;
    final Approach approach;
    final CombatActuator combat;
    final RetreatExecutor retreating;

    final TacticsOrders orders;
    final TacticsMetrics metrics;

    TacticsMemory memory;
    RetreatTracker retreat = RetreatTracker.IDLE;
    TacticalOutcome outcome;
    ExecutionResult result = ExecutionResult.idle();
    WorldFactCollector.Collected facts;
    Optional<RetreatPlan> plan = Optional.empty();
    RetreatMethod planMethod = RetreatMethod.NONE;
    TacticsAction action = TacticsAction.IDLE;
    final TacticsReturn returnWalk;
    String targetId;
    String shootTargetId;
    boolean active;
    private boolean paused;
    boolean forced;
    private boolean hurtPending;
    int lastHurtTick = -1_000_000;
    private long suspendedUntil = Long.MIN_VALUE;
    RetreatExecutor.Progress progress = RetreatExecutor.Progress.DONE;
    private final TacticsDecision decision;
    private final TacticsExecution execution;
    private final TacticsState state;

    public TacticsService(SpiritEntity spirit) {
        this.spirit = spirit;
        this.orders = new TacticsOrders(this);
        this.metrics = new TacticsMetrics(settings.journalCapacity(), settings.journalMaxPerSecond());
        this.rally = new TacticsRally(spirit);
        this.approach = new Approach(spirit);
        this.combat = new CombatActuator(spirit, approach);
        this.retreating = new RetreatExecutor(spirit, approach, combat, collector);
        this.returnWalk = new TacticsReturn(this, spirit, approach, retreating);
        this.decision = new TacticsDecision(this);
        this.execution = new TacticsExecution(this);
        this.state = new TacticsState(this);
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
                decision.evaluate(now, byEvent);
            }
            execution.execute();
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
        return state.snapshot();
    }

    public void restore(StoredTactics stored) {
        state.restore(stored);
    }

    public void failRestore(String reason) {
        metrics.failRestore(reason);
    }

    void release() {
        execution.release();
    }

    void logChange(TacticalOutcome next) {
        if (outcome == null
                || outcome.decision() != next.decision()
                || outcome.retreatMethod() != next.retreatMethod()) {
            LOGGER.debug("Дух {}: {}", spirit.getUUID(), TacticsTrace.format(next));
        }
    }
}
