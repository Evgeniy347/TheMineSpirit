package com.theminespirit.neoforge.tactics;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.Situation;
import com.theminespirit.core.tactics.TacticalOption;
import com.theminespirit.core.tactics.TacticalOutcome;
import com.theminespirit.core.tactics.TacticsConfig;
import com.theminespirit.core.tactics.TacticsEngine;
import com.theminespirit.core.tactics.TacticsMemory;
import com.theminespirit.core.tactics.adapter.CoreFactCollector;
import com.theminespirit.core.tactics.adapter.ExecutionResult;
import com.theminespirit.core.tactics.adapter.IntentActionExecutor;
import com.theminespirit.core.tactics.adapter.OrdersBuilder;
import com.theminespirit.core.tactics.adapter.TacticsAdapterSettings;
import com.theminespirit.core.tactics.adapter.TacticsSchedule;
import com.theminespirit.core.tactics.adapter.TacticsTrace;
import com.theminespirit.core.tactics.adapter.TargetDesignation;
import com.theminespirit.neoforge.Approach;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public final class TacticsService {
    public static final CombatMode DEFAULT_MODE = CombatMode.DEFENSIVE;
    public static final double RETURN_ARRIVAL = 1.5;
    public static final int FAILURE_BACKOFF_TICKS = 100;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final TacticsEngine ENGINE = new TacticsEngine(TacticsConfig.loadDefault());
    private static final int NEVER = -1_000_000;

    private final SpiritEntity spirit;
    private final TacticsAdapterSettings settings = TacticsAdapterSettings.loadDefault();
    private final WorldFactCollector collector = new WorldFactCollector(settings);
    private final CoreFactCollector situations = new CoreFactCollector(settings);
    private final IntentActionExecutor intents = new IntentActionExecutor();
    private final TacticsSchedule schedule = new TacticsSchedule(settings.evalIntervalTicks());
    private final Approach approach;
    private final CombatActuator combat;

    private CombatMode ordered = DEFAULT_MODE;
    private Optional<OrdersBuilder.Zone> zone = Optional.empty();
    private Optional<UUID> ward = Optional.empty();

    private TacticsMemory memory;
    private TacticalOutcome outcome;
    private ExecutionResult result = ExecutionResult.idle();
    private WorldFactCollector.Collected facts;
    private Optional<Point> returnTarget = Optional.empty();
    private String targetId;
    private boolean active;
    private boolean paused;
    private boolean forced;
    private boolean hurtPending;
    private boolean returned;
    private int lastHurtTick = NEVER;
    private long suspendedUntil = Long.MIN_VALUE;

    private int stepCalls;
    private int blockReads;
    private int entityQueries;
    private int failures;
    private String lastFailure;

    public TacticsService(SpiritEntity spirit) {
        this.spirit = spirit;
        this.approach = new Approach(spirit);
        this.combat = new CombatActuator(spirit, approach);
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

    public boolean wantsControl() {
        return active;
    }

    public Optional<TacticalOutcome> lastOutcome() {
        return Optional.ofNullable(outcome);
    }

    public ExecutionResult lastResult() {
        return result;
    }

    public CombatActuator combat() {
        return combat;
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

    public Optional<String> explain() {
        return Optional.ofNullable(outcome).map(TacticsTrace::format);
    }

    private void evaluate(long now, boolean byEvent) {
        TacticsInputs inputs = new TacticsInputs(ordered, zone, ward, ticksSince(lastHurtTick),
                ticksSince(combat.lastStrikeTick()));
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
        result = intents.execute(next, collected.context());
        Optional<EnemyFact> designated = TargetDesignation.pick(next, collected.context());
        targetId = designated.map(EnemyFact::id).orElse(null);
        spirit.setTarget(designated.map(enemy -> collected.entities().get(enemy.id())).orElse(null));
        returnTarget = next.returnTo();
        returned = false;
        logChange(next);
        outcome = next;
        facts = collected;
    }

    private void execute() {
        if (outcome == null) {
            return;
        }
        LivingEntity target = entity(targetId).orElse(null);
        if (outcome.decision() == TacticalOption.FIGHT && target != null) {
            active = true;
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
