package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.building.SpiritReach;
import com.theminespirit.core.coordinator.WorkKind;
import com.theminespirit.core.coordinator.WorkTarget;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.inventory.InventorySpace;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.mining.ApproachFailureTracker;
import com.theminespirit.core.mining.BreakSafety;
import com.theminespirit.core.mining.BreakSkipTally;
import com.theminespirit.core.mining.DropRetries;
import com.theminespirit.core.mining.MineTunnel;
import com.theminespirit.core.mining.MineVeinStands;
import com.theminespirit.core.mining.MiningPlanner;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.mining.OreKind;
import com.theminespirit.core.mining.ProgressWatchdog;
import com.theminespirit.core.mining.SoilLayer;
import com.theminespirit.core.mining.TargetBuffer;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.pickup.OverflowMode;
import com.theminespirit.core.pickup.PickupTuning;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.neoforge.coordinator.WorkReserveHub;
import com.theminespirit.neoforge.inventory.StackMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public final class HarvestSession {
    public enum Outcome {
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    private enum State {
        FIND,
        PICK,
        APPROACH,
        BREAK,
        COLLECT,
        DEPOSIT,
        TUNNEL
    }

    private static final double APPROACH_ARRIVAL = 0.6;
    private static final double APPROACH_PROGRESS_STEP = 0.05;
    private static final int COLLECT_RADIUS = 4;
    private static final Logger LOGGER = LogUtils.getLogger();

    private final SpiritEntity spirit;
    private final Level level;
    private final HarvestTargets targets;
    private final WorkKind kind;
    private final String label;
    private final int count;
    private final int radius;
    private final boolean skipWithoutTool;
    private final boolean vein;
    private final LevelSafetyView safety;
    private final BlockBreaker breaker;
    private final Approach approach;
    private final TargetBuffer buffer = new TargetBuffer();
    private final ProgressWatchdog watchdog = new ProgressWatchdog(CatalogLimits.HARVEST_NO_PROGRESS_TICKS);
    private final ProgressWatchdog veinMining = new ProgressWatchdog(CatalogLimits.HARVEST_NO_PROGRESS_TICKS);
    private final DropRetries dropRetries = new DropRetries(COLLECT_RADIUS);

    private State state = State.FIND;
    private Outcome outcome = Outcome.RUNNING;
    private String failureReason;
    private BlockCoord currentTarget;
    private BlockCoord currentStand;
    private BlockCoord lastUnreachable;
    private double closestApproach = Double.MAX_VALUE;
    private final ApproachFailureTracker approachFailures = new ApproachFailureTracker();
    private final Set<BlockCoord> reachStandsTried = new HashSet<>();
    private VeinTunnelSession tunnel;
    private final Set<BlockCoord> tunnelTried = new HashSet<>();
    private String tunnelRefusal;
    private DropCollector collector;
    private HarvestDeposit deposit;
    private boolean depositTried;
    private boolean depositFreed;
    private WorkTarget reserved;

    private int mined;
    private int unsafe;
    private final BreakSkipTally breakSkips = new BreakSkipTally();
    private int unreachable;
    private int noTool;
    private int protectedSkipped;
    private int planCalls;
    private int collectTicks;
    private int collectRuns;

    private int entityQueries;
    private final GatherCells terrain;

    private HarvestSession(SpiritEntity spirit, Spec spec) {
        this.spirit = spirit;
        this.level = spirit.level();
        this.targets = spec.targets();
        this.kind = spec.kind();
        this.label = spec.label();
        this.count = spec.count();
        this.radius = spec.radius();
        this.skipWithoutTool = spec.skipWithoutTool();
        this.vein = spec.vein();
        this.safety = new LevelSafetyView(level);
        this.breaker = new BlockBreaker(spirit);
        this.approach = new Approach(spirit);
        this.terrain = new GatherCells(level);
    }

    public static HarvestSession mineVein(SpiritEntity spirit, OreKind ore, int count, int radius) {
        Set<Block> ores = BlockSets.resolve(IdFilter.of(List.copyOf(OreKind.blocks(ore))));
        return new HarvestSession(
                spirit,
                new Spec(
                        new VeinTargets(spirit.level(), spirit.blockPosition(), radius, ores),
                        WorkKind.VEIN,
                        ore.name().toLowerCase(Locale.ROOT),
                        count,
                        radius,
                        ore == OreKind.ANY,
                        true));
    }

    public static HarvestSession gather(SpiritEntity spirit, String block, int count, int radius) {
        return new HarvestSession(
                spirit,
                new Spec(
                        new GatherTargets(
                                spirit.level(),
                                spirit.blockPosition(),
                                radius,
                                IdFilter.of(SoilLayer.dirt(block) ? SoilLayer.TURF_BLOCKS : List.of(block)),
                                spirit.home().isPresent()),
                        WorkKind.GATHER,
                        block,
                        count,
                        radius,
                        IdFilter.isPattern(block),
                        false));
    }

    public Outcome tick() {
        if (outcome != Outcome.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время добычи");
        }
        if (mined >= count && collector == null) {
            if (dropRetries.isEmpty()) {
                outcome = Outcome.SUCCEEDED;
                return outcome;
            }
            beginRetryCollect();
        }

        watchdog.tick();
        if (vein && mined == 0) {
            veinMining.tick();
        }
        ProgressWatchdog stalled =
                watchdog.stalled() ? watchdog : vein && mined == 0 && veinMining.stalled() ? veinMining : null;
        if (stalled != null) {
            return fail(HarvestReasonText.stallReason(
                    reasonTally(), stalled.limitTicks(), searching(), state.name(), targetForReason()));
        }
        switch (state) {
            case FIND -> find();
            case PICK -> pick();
            case APPROACH -> approachTick();
            case BREAK -> breakTick();
            case COLLECT -> collectTick();
            case DEPOSIT -> depositTick();
            case TUNNEL -> tunnelTick();
        }
        return outcome;
    }

    public void cancel() {
        releaseReserved();
        breaker.cancel();
        approach.cancel();
        if (collector != null) {
            collector.cancel();
        }
        if (deposit != null) {
            deposit.cancel();
        }
        if (tunnel != null) {
            tunnel.cancel();
        }
    }

    public void suspend() {
        approach.suspend();
        if (state == State.BREAK) {
            breaker.cancel();
            buffer.offer(List.of(currentTarget));
            currentTarget = null;
            currentStand = null;
            state = State.PICK;
        }
        if (collector != null) {
            collector.suspend();
        }
        if (deposit != null) {
            deposit.suspend();
        }
        if (tunnel != null) {
            tunnel.suspend();
        }
    }

    public int crackStage() {
        return breaker.crackStage();
    }

    public Outcome outcome() {
        return outcome;
    }

    public String failureReason() {
        return failureReason;
    }

    public ScriptInstance.Progress progress() {
        return new ScriptInstance.Progress(Math.min(mined, count), count);
    }

    public int mined() {
        return mined;
    }

    public Map<String, Object> saveState() {
        return Map.of("mined", mined);
    }

    public void restoreState(Map<String, Object> state) {
        Object value = state.get("mined");
        if (value instanceof Number number) {
            mined = Math.max(mined, number.intValue());
        }
    }

    public int planCalls() {
        return planCalls;
    }

    public int slicesUsed() {
        return targets.slicesUsed();
    }

    public int collectTicks() {
        return collectTicks;
    }

    public int collectRuns() {
        return collectRuns;
    }

    public int entityQueries() {
        return entityQueries;
    }

    public int maxSlicesPerTick() {
        return targets.maxSlicesPerTick();
    }

    private void find() {
        if (targets.refusalReason() != null) {
            fail(targets.refusalReason());
            return;
        }
        if (!buffer.needsRefill()) {
            state = State.PICK;
            return;
        }
        buffer.offer(targets.advance());
        if (!buffer.isEmpty()) {
            state = State.PICK;
            return;
        }
        if (targets.done()) {
            fail(HarvestReasonText.stopReason(
                    reasonTally(), radius, vein, kind == WorkKind.GATHER, lastUnreachable, tunnelRefusal));
        }
    }

    private void pick() {
        if (buffer.isEmpty()) {
            state = State.FIND;
            return;
        }
        if (buffer.needsRefill() && !targets.done()) {
            buffer.offer(targets.advance());
        }
        planCalls++;
        BlockPos from = spirit.blockPosition();
        BlockCoord origin = new BlockCoord(from.getX(), from.getY(), from.getZ());
        List<BlockCoord> candidates = buffer.targets(origin);
        if (kind == WorkKind.GATHER) {
            candidates = terrain.shallow(candidates, buffer);
            if (candidates.isEmpty()) {
                state = State.FIND;
                return;
            }
        }
        for (BlockCoord candidate : candidates) {
            boolean ownColumn = candidate.x() == origin.x() && candidate.z() == origin.z();
            if (SpiritReach.reachable(origin, candidate)
                    && !ownColumn
                    && (vein || terrain.exposed(candidate))
                    && !approachFailures.excludedStands().contains(origin)) {
                if (acceptTarget(candidate)) {
                    currentStand = origin;
                    prepareBreak();
                }
                return;
            }
        }
        MiningPlanner.Outcome result =
                MiningPlanner.next(origin, candidates, safety, approachFailures.excludedStands(), this::standReachable);
        if (result instanceof MiningPlanner.Exhausted exhausted) {
            if (exhausted.noStand() > 0 || exhausted.unreachable() > 0) {
                if (lastUnreachable == null && !candidates.isEmpty()) {
                    lastUnreachable = candidates.get(0);
                }
                if (approachReachStand(origin, candidates)) {
                    return;
                }
                if (vein && beginTunnel(origin, candidates)) {
                    return;
                }
            }
            unsafe += exhausted.unsafe();
            breakSkips.record(BreakSkipTally.Reason.NO_STAND, exhausted.noStand());
            unreachable += exhausted.unreachable();
            buffer.clearActive();
            state = State.FIND;
            return;
        }
        MiningPlanner.Next next = (MiningPlanner.Next) result;
        if (kind == WorkKind.GATHER && !standReachable(next.stand())) {
            unreachable++;
            lastUnreachable = next.target();
            approachFailures.excludeStand(next.stand(), next.target());
            state = State.PICK;
            return;
        }
        if (!acceptTarget(next.target())) {
            return;
        }
        currentStand = next.stand();
        beginApproach(
                new Vec3(next.stand().x() + 0.5, next.stand().y(), next.stand().z() + 0.5));
        state = State.APPROACH;
    }

    private boolean standReachable(BlockCoord stand) {
        return HarvestStands.reachable(spirit, stand);
    }

    private void beginApproach(Vec3 point) {
        closestApproach = Double.MAX_VALUE;
        approach.begin(point, APPROACH_ARRIVAL);
    }

    private void noteApproachProgress() {
        if (currentStand == null) {
            return;
        }
        double distance = spirit.position()
                .distanceTo(new Vec3(currentStand.x() + 0.5, currentStand.y(), currentStand.z() + 0.5));
        if (distance + APPROACH_PROGRESS_STEP < closestApproach) {
            closestApproach = distance;
            watchdog.progress();
        }
    }

    private boolean acceptTarget(BlockCoord target) {
        BlockState targetState = level.getBlockState(toPos(target));
        buffer.remove(target);
        if (!breaker.dropsAllowedFor(targetState)) {
            if (breaker.toolBroke()) {
                fail("инструмент сломался: добыто " + mined + " из " + count);
                return false;
            }
            if (mined == 0 && !skipWithoutTool) {
                fail(HarvestReasonText.tierReason(targetState, label));
                return false;
            }
            noTool++;
            return false;
        }
        currentTarget = target;
        return true;
    }

    private boolean approachReachStand(BlockCoord origin, List<BlockCoord> candidates) {
        List<BlockCoord> reachable =
                vein ? candidates : candidates.stream().filter(terrain::exposed).toList();
        Optional<BlockCoord> stand =
                MineVeinStands.next(reachable, origin, safety, approachFailures.excludedStands(), reachStandsTried);
        if (stand.isEmpty()) {
            unreachable++;
            if (!reachable.isEmpty()) {
                lastUnreachable = reachable.get(0);
            }
            return false;
        }
        if (kind == WorkKind.GATHER && !standReachable(stand.get())) {
            unreachable++;
            lastUnreachable = reachable.isEmpty() ? null : reachable.get(0);
            return false;
        }
        reachStandsTried.add(stand.get());
        currentTarget = null;
        currentStand = stand.get();
        beginApproach(new Vec3(currentStand.x() + 0.5, currentStand.y(), currentStand.z() + 0.5));
        state = State.APPROACH;
        return true;
    }

    private boolean beginTunnel(BlockCoord origin, List<BlockCoord> candidates) {
        if (tunnel != null) {
            return true;
        }
        String refusal = tunnelRefusal;
        for (BlockCoord candidate : candidates) {
            if (!tunnelTried.add(candidate)) {
                continue;
            }
            MineTunnel.Plan plan = MineTunnel.plan(candidate, origin, safety, radius);
            tunnel = VeinTunnelSession.start(spirit, candidate, plan, safety);
            if (tunnel != null) {
                state = State.TUNNEL;
                return true;
            }
            refusal = plan.refusal();
        }
        tunnelRefusal = refusal;
        return false;
    }

    private void tunnelTick() {
        watchdog.progress();
        veinMining.progress();
        VeinTunnelSession.Outcome progress = tunnel.tick();
        if (progress == VeinTunnelSession.Outcome.RUNNING) {
            return;
        }
        if (progress == VeinTunnelSession.Outcome.FAILED) {
            tunnelRefusal = tunnel.failureReason();
        }
        tunnel = null;
        state = State.PICK;
    }

    private void approachTick() {
        noteApproachProgress();
        switch (approach.tick()) {
            case MOVING -> {}
            case ARRIVED -> {
                if (currentTarget == null) {
                    state = State.PICK;
                } else {
                    prepareBreak();
                }
            }
            case STUCK, TIMEOUT -> {
                if (currentTarget == null) {
                    currentStand = null;
                    state = State.PICK;
                    return;
                }
                if (approachFailures.recordFailure(currentStand, currentTarget)) {
                    unreachable++;
                } else {
                    buffer.offer(List.of(currentTarget));
                }
                currentTarget = null;
                currentStand = null;
                state = State.PICK;
            }
        }
    }

    private void prepareBreak() {
        if (!(MiningSafety.canBreak(currentTarget, safety) instanceof BreakSafety.Safe)) {
            unsafe++;
            currentTarget = null;
            currentStand = null;
            state = State.PICK;
            return;
        }
        BlockPos feet = spirit.blockPosition();
        boolean inReach = SpiritReach.reachable(new BlockCoord(feet.getX(), feet.getY(), feet.getZ()), currentTarget);
        BlockBreaker.Begin begin =
                inReach ? breaker.beginAnyDistance(toPos(currentTarget)) : breaker.begin(toPos(currentTarget));
        if (begin instanceof BlockBreaker.Protected) {
            protectedSkipped++;
            currentTarget = null;
            currentStand = null;
            state = State.PICK;
            return;
        }
        if (begin instanceof BlockBreaker.Refused refused) {
            if (refused.reason() == BlockBreaker.RefuseReason.NEED_TOOL) {
                fail(refused.message());
                return;
            }
            BreakSkipTally.Reason reason = BreakSkips.reason(refused.reason());
            breakSkips
                    .record(reason, currentTarget, currentStand)
                    .ifPresent(sample -> SkipLog.skipped(LOGGER, spirit, "harvest", sample));
            if (BreakSight.retryable(refused.reason()) && retryOtherStand()) {
                return;
            }
            currentTarget = null;
            currentStand = null;
            state = State.PICK;
            return;
        }
        if (breaker.dropsAllowed()) {
            BlockPos pos = toPos(currentTarget);
            BlockState stateToBreak = level.getBlockState(pos);
            BlockEntity blockEntity = level.getBlockEntity(pos);
            ItemStack tool = breaker.tool();
            List<ItemStack> drops = Block.getDrops(stateToBreak, (ServerLevel) level, pos, blockEntity, spirit, tool);
            StackMapper mapper = new StackMapper();
            List<Stack> inventory = mapper.map(spirit.getInventory());
            List<Stack> incoming = new ArrayList<>();
            for (ItemStack drop : drops) {
                if (!drop.isEmpty()) {
                    incoming.add(mapper.of(drop));
                }
            }
            boolean fits = incoming.isEmpty() || InventorySpace.holds(inventory, incoming);
            boolean nearlyFull = HarvestDeposit.occupiedSlots(spirit) >= CatalogLimits.UNLOAD_TRIGGER_SLOTS;
            if (!fits || (nearlyFull && !depositTried)) {
                if (!depositTried) {
                    startDeposit(incoming, !fits);
                    return;
                }
                fail("инвентарь полон: не помещается добыча");
                return;
            }
        }
        WorkTarget target = new WorkTarget(kind, currentTarget);
        if (!WorkReserveHub.reserve(spirit, target)) {
            breaker.cancel();
            currentTarget = null;
            currentStand = null;
            state = State.PICK;
            return;
        }
        reserved = target;
        state = State.BREAK;
    }

    private boolean retryOtherStand() {
        if (currentTarget == null) {
            return false;
        }
        BlockCoord target = currentTarget;
        lastUnreachable = target;
        if (currentStand != null) {
            approachFailures.excludeStand(currentStand, target);
        }
        buffer.offer(List.of(target));
        currentTarget = null;
        currentStand = null;
        state = State.PICK;
        return true;
    }

    private void releaseReserved() {
        if (reserved != null) {
            WorkReserveHub.release(spirit, reserved);
            reserved = null;
        }
    }

    private void startDeposit(List<Stack> incoming, boolean mandatory) {
        int toolSlot = breaker.toolSlot();
        breaker.cancel();
        approach.cancel();
        depositTried = true;
        depositFreed = false;
        deposit = new HarvestDeposit(spirit, incoming, mandatory, toolSlot);
        buffer.offer(List.of(currentTarget));
        currentTarget = null;
        currentStand = null;
        state = State.DEPOSIT;
    }

    private void depositTick() {
        HarvestDeposit.Step step = deposit.tick();
        if (step == HarvestDeposit.Step.RUNNING) {
            return;
        }
        String reason = deposit.failureReason();
        depositFreed = deposit.freed();
        deposit = null;
        if (step == HarvestDeposit.Step.FAILED) {
            fail(reason);
            return;
        }
        state = State.PICK;
    }

    private void breakTick() {
        switch (breaker.tick()) {
            case BREAKING -> {}
            case BROKEN -> {
                releaseReserved();
                mined++;
                watchdog.progress();
                veinMining.progress();
                collectRuns++;
                collector = newCollector(currentTarget);
                state = State.COLLECT;
            }
            case GONE -> {
                releaseReserved();
                currentTarget = null;
                currentStand = null;
                state = State.PICK;
            }
        }
    }

    private void collectTick() {
        collectTicks++;
        DropCollector.State collected = collector.tick();
        if (collected == DropCollector.State.RUNNING) {
            return;
        }
        entityQueries += collector.entityQueries();
        if (collected == DropCollector.State.FAILED) {
            String reason = collector.failureReason();
            if (mined >= count) {
                fail("не удалось подобрать дроп у " + currentTarget + ": " + reason);
                return;
            }
            dropRetries.failed(currentTarget);
            collector = null;
            currentTarget = null;
            currentStand = null;
            state = State.PICK;
            return;
        }
        collector = null;
        currentTarget = null;
        currentStand = null;
        if (depositFreed || HarvestDeposit.occupiedSlots(spirit) < CatalogLimits.UNLOAD_TRIGGER_SLOTS) {
            depositTried = false;
            depositFreed = false;
        }
        state = State.PICK;
    }

    private void beginRetryCollect() {
        currentTarget = dropRetries.poll();
        currentStand = null;
        collector = newCollector(currentTarget);
        state = State.COLLECT;
    }

    private DropCollector newCollector(BlockCoord center) {
        return new DropCollector(
                spirit,
                IdFilter.matchAll(),
                center,
                COLLECT_RADIUS,
                PickupTuning.DEFAULT,
                OverflowMode.forSpirit(spirit.isFollowingPlayer()));
    }

    private Outcome fail(String reason) {
        outcome = Outcome.FAILED;
        failureReason = reason;
        cancel();
        return outcome;
    }

    private HarvestReasonText.Tally reasonTally() {
        return new HarvestReasonText.Tally(mined, count, unsafe, noTool, protectedSkipped, unreachable, breakSkips);
    }

    private boolean searching() {
        return mined == 0 && (state == State.FIND || state == State.PICK || state == State.APPROACH);
    }

    private BlockCoord targetForReason() {
        if (currentTarget != null) {
            return currentTarget;
        }
        return lastUnreachable;
    }

    private static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }

    private record Spec(
            HarvestTargets targets,
            WorkKind kind,
            String label,
            int count,
            int radius,
            boolean skipWithoutTool,
            boolean vein) {}
}
