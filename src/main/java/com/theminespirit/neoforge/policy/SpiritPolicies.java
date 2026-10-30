package com.theminespirit.neoforge.policy;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.AirPolicy;
import com.theminespirit.core.policy.BodyFlags;
import com.theminespirit.core.policy.ExplosionAvoidance;
import com.theminespirit.core.policy.HazardReflex;
import com.theminespirit.core.policy.HungerPolicy;
import com.theminespirit.core.policy.LayerArbiter;
import com.theminespirit.core.policy.LocalSurroundings;
import com.theminespirit.core.policy.PolicyExemptions;
import com.theminespirit.core.policy.PolicyId;
import com.theminespirit.core.policy.PolicyVerdict;
import com.theminespirit.core.policy.PortalPolicy;
import com.theminespirit.core.policy.ReflexAction;
import com.theminespirit.core.policy.ReflexSchedule;
import com.theminespirit.core.policy.ReflexTimeout;
import com.theminespirit.core.policy.WorkZone;
import com.theminespirit.core.policy.ZonePolicy;
import com.theminespirit.core.tactics.Layer;
import com.theminespirit.neoforge.LevelSafetyView;
import com.theminespirit.neoforge.PositionTrace;
import com.theminespirit.neoforge.SpiritBrain;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

public final class SpiritPolicies {
    public static final int REFLEX_TIMEOUT_TICKS = 100;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final PolicyVerdict CLEAR = new PolicyVerdict.Clear();

    private final SpiritEntity spirit;
    private final LayerArbiter arbiter = new LayerArbiter();
    private final ReflexExecutor executor = new ReflexExecutor();
    private final PositionTrace positions = new PositionTrace();

    private PolicyExemptions exemptions = PolicyExemptions.empty();
    private PolicyExemptions configured;
    private BlockPos lastFeet;
    private PolicyVerdict hazard = CLEAR;
    private PolicyVerdict portal = CLEAR;
    private PolicyVerdict explosion = CLEAR;
    private PolicyVerdict verdict = CLEAR;
    private Optional<BlockCoord> waterTarget = Optional.empty();
    private long waterSearchedAt = -ReflexSchedule.PERIOD_TICKS;
    private WorkZone workZone;
    private final ReflexTimeout reflexTimeout = new ReflexTimeout();
    private Layer suspendedBy;
    private boolean hadReflex;
    private long suppressedUntil = Long.MIN_VALUE;
    private int phase = -1;

    private int blockReads;
    private int entityQueries;
    private int tierOneRuns;

    public SpiritPolicies(SpiritEntity spirit) {
        this.spirit = spirit;
    }

    public void tick() {
        Level level = spirit.level();
        if (level.isClientSide()) {
            return;
        }
        long now = level.getGameTime();
        positions.tick(spirit);
        refreshExemptions();
        BodyFlags flags = bodyFlags();
        BlockPos feet = spirit.blockPosition();
        boolean blockChanged = !feet.equals(lastFeet);
        lastFeet = feet;
        boolean hazardActive = hazard instanceof PolicyVerdict.Reflex || portal instanceof PolicyVerdict.Reflex;
        boolean escalated = hazardActive || flags.inLava() || flags.onFire() || flags.inWater() || flags.hurtRecently();
        if (ReflexSchedule.tierOneDue(now, phase(), blockChanged, escalated)) {
            readTierOne(now, flags);
        }
        if (explosion instanceof PolicyVerdict.Reflex || ReflexSchedule.tierTwoDue(now, phase())) {
            scanThreats();
        }
        verdict = guardTimeout(combine(), now);
        Layer owner = arbiter.step(verdict, spirit.tactics().wantsControl(), hasRunningTask());
        updateSuspension(owner);
        if (verdict instanceof PolicyVerdict.Reflex reflex) {
            executor.execute(spirit, reflex.action());
        } else if (hadReflex) {
            executor.release(spirit);
        }
        hadReflex = verdict instanceof PolicyVerdict.Reflex;
    }

    public PolicyVerdict verdict() {
        return verdict;
    }

    public boolean isOverriding() {
        return arbiter.owner() == Layer.POLICY;
    }

    public Layer owner() {
        return arbiter.owner();
    }

    public PolicyExemptions exemptions() {
        return exemptions;
    }

    public Optional<WorkZone> workZone() {
        return Optional.ofNullable(workZone);
    }

    public void setWorkZone(WorkZone zone) {
        this.workZone = zone;
    }

    public void clearWorkZone() {
        this.workZone = null;
    }

    public int blockReads() {
        return blockReads;
    }

    public int entityQueries() {
        return entityQueries;
    }

    public int tierOneRuns() {
        return tierOneRuns;
    }

    private int phase() {
        if (phase < 0) {
            phase = ReflexSchedule.offsetOf(spirit.getUUID());
        }
        return phase;
    }

    private void refreshExemptions() {
        exemptions = spirit.brainIfPresent()
                .map(SpiritBrain::policyExemptions)
                .orElse(PolicyExemptions.empty())
                .union(spirit.tactics().exemptions());
        if (!exemptions.equals(configured)) {
            PathingConfigurator.apply(spirit, exemptions);
            configured = exemptions;
        }
    }

    private BodyFlags bodyFlags() {
        boolean swimmingAllowed =
                hasRunningTask() || spirit.tactics().exemptions().has(PolicyId.WATER);
        return new BodyFlags(
                spirit.isInLava(), spirit.isOnFire(), spirit.isInWater() && !swimmingAllowed, spirit.hurtTime > 0);
    }

    private boolean hasRunningTask() {
        return spirit.brainIfPresent()
                .flatMap(SpiritBrain::status)
                .map(status -> status == RunStatus.RUNNING)
                .orElse(false);
    }

    private void readTierOne(long now, BodyFlags flags) {
        Optional<BlockCoord> water = Optional.empty();
        if (flags.onFire() && !flags.inWater()) {
            if (now - waterSearchedAt >= ReflexSchedule.PERIOD_TICKS) {
                SurroundingsReader.WaterSearch found = SurroundingsReader.nearestWater(spirit);
                blockReads += found.reads();
                waterTarget = found.cell();
                waterSearchedAt = now;
            }
            water = waterTarget;
        } else {
            waterTarget = Optional.empty();
            waterSearchedAt = -ReflexSchedule.PERIOD_TICKS;
        }
        LevelSafetyView view = new LevelSafetyView(spirit.level());
        LocalSurroundings surroundings = SurroundingsReader.read(spirit, view, flags, water);
        blockReads += view.reads();
        tierOneRuns++;
        hazard = HazardReflex.evaluate(surroundings, exemptions);
        portal = PortalPolicy.evaluate(surroundings, exemptions);
    }

    private void scanThreats() {
        List<ExplosionAvoidance.ThreatFact> threats = ThreatScanner.scan(spirit);
        entityQueries++;
        if (threats.isEmpty()) {
            explosion = CLEAR;
            return;
        }
        LevelSafetyView view = new LevelSafetyView(spirit.level());
        explosion = ExplosionAvoidance.evaluate(feetCoord(), threats, view);
        blockReads += view.reads();
    }

    private PolicyVerdict combine() {
        if (hazard instanceof PolicyVerdict.Reflex reflex && reflex.policy() == PolicyId.LAVA_FIRE) {
            return hazard;
        }
        PolicyVerdict air = AirPolicy.evaluate(spirit.getAirSupply(), spirit.isUnderWater());
        if (air instanceof PolicyVerdict.Reflex) {
            return air;
        }
        if (explosion instanceof PolicyVerdict.Reflex) {
            return explosion;
        }
        if (hazard instanceof PolicyVerdict.Reflex) {
            return hazard;
        }
        if (portal instanceof PolicyVerdict.Reflex) {
            return portal;
        }
        if (workZone != null) {
            PolicyVerdict zone = ZonePolicy.evaluateInside(feetCoord(), workZone);
            if (zone instanceof PolicyVerdict.Reflex) {
                return zone;
            }
        }
        return HungerPolicy.evaluate(
                spirit.hunger().foodLevel(),
                spirit.hunger().rules(),
                spirit.hunger().hasFood(),
                spirit.tactics().wantsControl());
    }

    private PolicyVerdict guardTimeout(PolicyVerdict candidate, long now) {
        if (now < suppressedUntil) {
            return CLEAR;
        }
        if (!(candidate instanceof PolicyVerdict.Reflex reflex)) {
            reflexTimeout.reset();
            return candidate;
        }
        String key = reflex.policy() + ":" + reflex.action().getClass().getSimpleName();
        if (!reflexTimeout.expired(reflex.policy(), key, REFLEX_TIMEOUT_TICKS, spirit.isUnderWater())) {
            return candidate;
        }
        LOGGER.warn(
                "Дух {} не завершил рефлекс {} за {} тиков и остановлен: {}",
                spirit.getUUID(),
                key,
                REFLEX_TIMEOUT_TICKS,
                reflex.reason());
        suppressedUntil = now + REFLEX_TIMEOUT_TICKS;
        reflexTimeout.reset();
        return new PolicyVerdict.Reflex(
                reflex.policy(),
                new ReflexAction.StopMoving(),
                "рефлекс не завершился за " + REFLEX_TIMEOUT_TICKS + " тиков: " + reflex.reason());
    }

    private void updateSuspension(Layer owner) {
        Layer controller = owner == Layer.POLICY || owner == Layer.TACTICS ? owner : null;
        if (controller == Layer.TACTICS
                && spirit.brainIfPresent()
                        .map(SpiritBrain::observesWhileTacticsOwns)
                        .orElse(false)) {
            controller = null;
        }
        if (controller != null && controller != suspendedBy) {
            String reason = suspendReason(controller);
            spirit.brainIfPresent().ifPresent(brain -> brain.suspend(reason));
        } else if (controller == null && suspendedBy != null) {
            spirit.brainIfPresent().ifPresent(SpiritBrain::resume);
        }
        suspendedBy = controller;
    }

    private String suspendReason(Layer owner) {
        if (owner == Layer.TACTICS) {
            return "тактика: бой или отход";
        }
        return verdict instanceof PolicyVerdict.Reflex reflex
                ? reflex.policy() + ": " + reflex.reason()
                : "политика безопасности";
    }

    private BlockCoord feetCoord() {
        BlockPos feet = spirit.blockPosition();
        return new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
    }
}
