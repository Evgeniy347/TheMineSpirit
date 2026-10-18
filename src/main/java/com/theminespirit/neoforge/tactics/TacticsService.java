package com.theminespirit.neoforge.tactics;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.coordinator.HelpCall;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.PolicyExemptions;
import com.theminespirit.core.policy.PolicyId;
import com.theminespirit.core.tactics.CombatDirective;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.DirectiveOwner;
import com.theminespirit.core.tactics.DirectiveOwnership;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.RallyTuning;
import com.theminespirit.core.tactics.RetreatMethod;
import com.theminespirit.core.tactics.SafePointChoice;
import com.theminespirit.core.tactics.Situation;
import com.theminespirit.core.tactics.TacticalOption;
import com.theminespirit.core.tactics.TacticalOutcome;
import com.theminespirit.core.tactics.TacticsConfig;
import com.theminespirit.core.tactics.TacticsEngine;
import com.theminespirit.core.tactics.TacticsMemory;
import com.theminespirit.core.tactics.adapter.CoreFactCollector;
import com.theminespirit.core.tactics.adapter.DecisionFacts;
import com.theminespirit.core.tactics.adapter.DecisionJournal;
import com.theminespirit.core.tactics.adapter.DecisionObservation;
import com.theminespirit.core.tactics.adapter.DecisionOrder;
import com.theminespirit.core.tactics.adapter.DecisionRecord;
import com.theminespirit.core.tactics.adapter.DecisionText;
import com.theminespirit.core.tactics.adapter.ExecutionResult;
import com.theminespirit.core.tactics.adapter.HelpCallGate;
import com.theminespirit.core.tactics.adapter.IntentActionExecutor;
import com.theminespirit.core.tactics.adapter.OrdersBuilder;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public final class TacticsService {
    public static final CombatMode DEFAULT_MODE = CombatMode.DEFENSIVE;
    public static final double RETURN_ARRIVAL = 1.5;
    public static final int FAILURE_BACKOFF_TICKS = 100;
    public static final int DECISIONS_IN_CHAT = 5;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final TacticsEngine ENGINE = new TacticsEngine(TacticsConfig.loadDefault());
    private static final int NEVER = -1_000_000;

    private final SpiritEntity spirit;
    private final TacticsAdapterSettings settings = TacticsAdapterSettings.loadDefault();
    private final WorldFactCollector collector = new WorldFactCollector(settings);
    private final CoreFactCollector situations = new CoreFactCollector(settings);
    private final IntentActionExecutor intents = new IntentActionExecutor();
    private final TacticsSchedule schedule = new TacticsSchedule(settings.evalIntervalTicks());
    private final DecisionJournal journal = new DecisionJournal(settings.journalCapacity(),
            settings.journalMaxPerSecond());
    private final HelpCallGate helpGate = new HelpCallGate();
    private final Approach approach;
    private final CombatActuator combat;
    private final RetreatExecutor retreating;

    private CombatMode ordered = DEFAULT_MODE;
    private Optional<OrdersBuilder.Zone> zone = Optional.empty();
    private Optional<UUID> ward = Optional.empty();

    private TacticsMemory memory;
    private RetreatTracker retreat = RetreatTracker.IDLE;
    private TacticalOutcome outcome;
    private ExecutionResult result = ExecutionResult.idle();
    private WorldFactCollector.Collected facts;
    private Optional<RetreatPlan> plan = Optional.empty();
    private RetreatMethod planMethod = RetreatMethod.NONE;
    private Optional<Point> returnTarget = Optional.empty();
    private CombatDirective directive;
    private final DirectiveOwnership ownership = new DirectiveOwnership();
    private Optional<StoredTactics> preDirective = Optional.empty();
    private LivingEntity forcedTarget;
    private RetreatMethod forcedMethod = RetreatMethod.NONE;
    private Optional<Point> forcedPoint = Optional.empty();
    private String targetId;
    private String shootTargetId;
    private boolean active;
    private boolean paused;
    private boolean forced;
    private boolean hurtPending;
    private boolean returned;
    private int lastHurtTick = NEVER;
    private long suspendedUntil = Long.MIN_VALUE;
    private RetreatExecutor.Progress progress = RetreatExecutor.Progress.DONE;

    private int stepCalls;
    private int blockReads;
    private int entityQueries;
    private int failures;
    private int helpCalls;
    private String lastFailure;
    private String restoreFailure;
    private Optional<HelpCall> helpCall = Optional.empty();

    public TacticsService(SpiritEntity spirit) {
        this.spirit = spirit;
        this.approach = new Approach(spirit);
        this.combat = new CombatActuator(spirit, approach);
        this.retreating = new RetreatExecutor(spirit, approach, combat, collector);
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
            failures++;
            lastFailure = error.toString();
            suspendedUntil = now + FAILURE_BACKOFF_TICKS;
            release();
            LOGGER.error("Дух {} не провёл тактику и приостановил её на {} тиков: {}", spirit.getUUID(),
                    FAILURE_BACKOFF_TICKS, error.getMessage(), error);
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
        if (mode == null) {
            throw new IllegalArgumentException("Режим боя = null: допустимы " + Arrays.toString(CombatMode.values()));
        }
        ordered = mode;
        forced = true;
        if (mode == CombatMode.PASSIVE) {
            spirit.setTarget(null);
        }
    }

    public CombatMode combatMode() {
        return ordered;
    }

    public void setGuardZone(BlockCoord center, double radius) {
        zone = Optional.of(new OrdersBuilder.Zone(new Point(center.x() + 0.5, center.y(), center.z() + 0.5), radius));
        forced = true;
    }

    public void clearGuardZone() {
        zone = Optional.empty();
        forced = true;
    }

    public void setWard(UUID id) {
        ward = Optional.ofNullable(id);
        forced = true;
    }

    public void applyDirective(CombatDirective directive) {
        applyDirective(directive, DirectiveOwner.SCRIPT);
    }

    public void applyDirective(CombatDirective directive, DirectiveOwner owner) {
        if (directive == null) {
            throw new IllegalArgumentException("Приказ = null: боевой скрипт обязан передать CombatDirective, "
                    + "иначе тактика не знает, какой режим и цель включить");
        }
        if (this.directive == null) {
            preDirective = Optional.of(snapshot());
        }
        this.directive = directive;
        ownership.appliedBy(owner);
        ordered = directive.mode();
        forcedTarget = null;
        setForcedRetreat(directive.retreat().orElse(RetreatMethod.NONE), directive.safePoint());
        if (directive.mode() == CombatMode.PASSIVE) {
            spirit.setTarget(null);
        }
        forced = true;
    }

    public boolean clearDirective() {
        return clearDirective(DirectiveOwner.SCRIPT);
    }

    public boolean clearDirective(DirectiveOwner owner) {
        if (directive == null || !ownership.mayClear(owner)) {
            return false;
        }
        directive = null;
        ownership.cleared();
        forcedTarget = null;
        setForcedRetreat(RetreatMethod.NONE, Optional.empty());
        restore(preDirective.orElse(new StoredTactics(DEFAULT_MODE, Optional.empty(), Optional.empty(),
                Optional.empty(), RetreatTracker.IDLE)));
        preDirective = Optional.empty();
        spirit.setTarget(null);
        return true;
    }

    public Optional<CombatDirective> activeDirective() {
        return Optional.ofNullable(directive);
    }

    public void setForcedTarget(LivingEntity target) {
        forcedTarget = target;
        spirit.setTarget(target);
    }

    public void setForcedRetreat(RetreatMethod method, Optional<Point> point) {
        if (method == null) {
            throw new IllegalArgumentException("Способ отхода = null: укажите RetreatMethod, "
                    + "для «способ не навязан» — RetreatMethod.NONE");
        }
        if (point == null) {
            throw new IllegalArgumentException("Точка отхода = null: отсутствие точки передаётся Optional.empty()");
        }
        if (point.isPresent() && method == RetreatMethod.NONE) {
            throw new IllegalArgumentException("Точка " + point.get() + " при способе NONE: "
                    + "точка имеет смысл только с явным способом отхода, иначе дух не знает, зачем она нужна");
        }
        forcedMethod = method;
        forcedPoint = point;
    }

    public boolean hasState() {
        return memory != null || ordered != DEFAULT_MODE || zone.isPresent() || ward.isPresent() || retreat.retreating();
    }

    public boolean wantsControl() {
        return active;
    }

    public PolicyExemptions exemptions() {
        return active && retreat.method() == RetreatMethod.WATER ? PolicyExemptions.of(PolicyId.WATER)
                : PolicyExemptions.empty();
    }

    public Optional<TacticalOutcome> lastOutcome() {
        return Optional.ofNullable(outcome);
    }

    public ExecutionResult lastResult() {
        return result;
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
        return stepCalls;
    }

    public int blockReads() {
        return blockReads;
    }

    public int entityQueries() {
        return entityQueries;
    }

    public int failures() {
        return failures;
    }

    public Optional<String> lastFailure() {
        return Optional.ofNullable(lastFailure);
    }

    public Optional<String> restoreFailure() {
        return Optional.ofNullable(restoreFailure);
    }

    public Optional<String> explain() {
        return Optional.ofNullable(outcome).map(TacticsTrace::format);
    }

    public List<DecisionRecord> decisions(int limit) {
        return journal.last(limit);
    }

    public Optional<HelpCall> helpCall() {
        if (helpCall.isEmpty()) {
            return Optional.empty();
        }
        long now = spirit.level().getGameTime();
        RallyTuning tuning = TacticsConfig.loadDefault().rules().retreat().rally();
        return now - helpCall.get().raisedAt() <= tuning.callIntervalTicks() ? helpCall : Optional.empty();
    }

    public int helpCalls() {
        return helpCalls;
    }

    public StoredTactics snapshot() {
        return new StoredTactics(ordered, zone, ward.map(UUID::toString), Optional.ofNullable(memory), retreat);
    }

    public void restore(StoredTactics stored) {
        ordered = stored.ordered();
        zone = stored.zone();
        ward = stored.ward().map(UUID::fromString);
        memory = stored.memory().orElse(null);
        retreat = stored.retreat();
        forced = true;
    }

    public void failRestore(String reason) {
        restoreFailure = reason;
    }

    private void evaluate(long now, boolean byEvent) {
        TacticsInputs inputs = new TacticsInputs(ordered, zone, ward, ticksSince(lastHurtTick),
                ticksSince(combat.lastStrikeTick()), designatedTarget());
        WorldFactCollector.Collected collected = collector.collect(spirit, inputs);
        blockReads += collected.blockReads();
        entityQueries += collected.entityQueries();
        Situation situation = situations.collect(collected.context());
        if (memory == null) {
            memory = TacticsMemory.initial(ordered, now, situation.self().hpFraction());
        }
        TacticalOutcome next = ENGINE.step(situation, memory, now);
        stepCalls++;
        schedule.evaluated(now, byEvent);
        memory = next.memory();
        next = withForcedRetreat(next);
        retreat = RetreatCommitment.next(retreat, next, now, settings, progress == RetreatExecutor.Progress.BUILDING);
        result = intents.execute(next, collected.context());
        Optional<EnemyFact> designated = TargetDesignation.pick(next, collected.context());
        Optional<LivingEntity> forcedTarget = designatedTarget();
        if (forcedTarget.isPresent()) {
            targetId = designated.map(EnemyFact::id).orElse(null);
            spirit.setTarget(forcedTarget.get());
        } else {
            boolean engages = engages(designated);
            targetId = engages ? designated.map(EnemyFact::id).orElse(null) : null;
            spirit.setTarget(engages ? designated.map(enemy -> collected.entities().get(enemy.id())).orElse(null) : null);
        }
        shootTargetId = collected.context().enemies().stream().filter(EnemyFact::visible)
                .min(Comparator.comparingDouble(enemy -> enemy.pos().distanceTo(situation.self().pos())))
                .map(EnemyFact::id).orElse(null);
        replan(collected, situation);
        returnTarget = next.returnTo();
        returned = false;
        logChange(next);
        journalDecision(now, next, situation);
        updateHelpCall(now, next, situation);
        outcome = next;
        facts = collected;
    }

    private void updateHelpCall(long now, TacticalOutcome next, Situation situation) {
        RallyTuning tuning = TacticsConfig.loadDefault().rules().retreat().rally();
        boolean calls = next.decision() == TacticalOption.RETREAT
                && situation.enemies().size() >= tuning.callMinEnemies();
        helpCall = calls ? Optional.of(new HelpCall(spirit.getUUID(), situation.self().pos(), now)) : Optional.empty();
        if (!calls || !helpGate.ready(now, tuning.callIntervalTicks())) {
            return;
        }
        ServerPlayer player = playerNear(tuning.callRadius());
        if (player == null) {
            return;
        }
        BlockPos at = spirit.blockPosition();
        player.sendSystemMessage(Component.literal("нужна помощь у " + at.getX() + " " + at.getY() + " " + at.getZ()));
        helpGate.called(now);
        helpCalls++;
    }

    private ServerPlayer playerNear(int radius) {
        return spirit.level().getNearestPlayer(spirit, radius) instanceof ServerPlayer player ? player : null;
    }

    private void journalDecision(long now, TacticalOutcome next, Situation situation) {
        DecisionOrder order = DecisionOrder.of(ordered, zone.isPresent(), ward.map(UUID::toString),
                activeDirective());
        journal.offer(new DecisionObservation(now, spirit.getUUID().toString(), spirit.displayName(), next,
                Optional.ofNullable(targetId), order, DecisionFacts.of(situation)))
                .ifPresent(record -> LOGGER.info("{}", DecisionText.format(record)));
    }

    private Optional<LivingEntity> designatedTarget() {
        return Optional.ofNullable(forcedTarget).filter(LivingEntity::isAlive);
    }

    private boolean engages(Optional<EnemyFact> designated) {
        if (ordered == CombatMode.PASSIVE) {
            return false;
        }
        if (TacticsConfig.loadDefault().modes().of(ordered).initiates()) {
            return true;
        }
        return spirit.getLastHurtByMob() != null || designated.map(EnemyFact::targetingSelf).orElse(false);
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

    private TacticalOutcome withForcedRetreat(TacticalOutcome next) {
        if (directive == null || directive.retreat().isEmpty()) {
            return next;
        }
        RetreatMethod method = forcedMethod != RetreatMethod.NONE ? forcedMethod
                : next.retreatMethod() != RetreatMethod.NONE ? next.retreatMethod() : RetreatMethod.AWAY;
        if (next.decision() == TacticalOption.RETREAT && next.retreatMethod() == method) {
            return next;
        }
        return new TacticalOutcome(TacticalOption.RETREAT, next.action(), method, next.callHelp(), next.state(),
                next.mode(), next.returnTo(), next.trace(), next.memory());
    }

    private Optional<RetreatPlan> planFor(WorldFactCollector.Collected collected, Situation situation) {
        BlockPos feet = spirit.blockPosition();
        Optional<Point> retreatPoint = forcedPoint.isPresent() ? forcedPoint
                : SafePointChoice.nearest(situation.self().pos(), collected.home(), collected.camp());
        RetreatPlanner.Input input = new RetreatPlanner.Input(situation.self().pos(),
                new BlockCoord(feet.getX(), feet.getY(), feet.getZ()), collected.context().enemies(), collected.env(),
                retreatPoint, situation.self().blocks(), collected.rally(), situation.self().hasWaterBucket());
        return RetreatPlanner.plan(retreat.method(), input);
    }

    private void execute() {
        if (outcome == null) {
            return;
        }
        if (retreat.retreating() && plan.isPresent()) {
            active = true;
            progress = retreating.step(plan.get(), entity(shootTargetId));
            return;
        }
        LivingEntity target = forcedTarget != null && forcedTarget.isAlive() ? forcedTarget
                : entity(targetId).orElse(null);
        if (outcome.decision() == TacticalOption.FIGHT && target != null) {
            active = true;
            retreating.reset();
            if (result instanceof ExecutionResult.Shooting && spirit.gear().arrows() > 0) {
                combat.shoot(target);
            } else {
                combat.melee(target);
            }
            return;
        }
        if (outcome.decision() == TacticalOption.IGNORE && returnTarget.isPresent() && !returned) {
            active = true;
            returnToAnchor(returnTarget.get());
            return;
        }
        release();
    }

    private void returnToAnchor(Point point) {
        retreating.reset();
        spirit.gear().holdNothing();
        Vec3 target = new Vec3(point.x(), point.y(), point.z());
        if (spirit.position().distanceTo(target) <= RETURN_ARRIVAL) {
            approach.cancel();
            returned = true;
            return;
        }
        if (!approach.isActive()) {
            approach.begin(target, RETURN_ARRIVAL);
        }
        approach.tick();
    }

    private void release() {
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
        if (outcome == null || outcome.decision() != next.decision() || outcome.retreatMethod() != next.retreatMethod()) {
            LOGGER.debug("Дух {}: {}", spirit.getUUID(), TacticsTrace.format(next));
        }
    }
}
