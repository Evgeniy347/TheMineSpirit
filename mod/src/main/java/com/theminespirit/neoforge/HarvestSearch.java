package com.theminespirit.neoforge;

import com.theminespirit.core.building.SpiritReach;
import com.theminespirit.core.coordinator.WorkKind;
import com.theminespirit.core.mining.ApproachFailureTracker;
import com.theminespirit.core.mining.MineVeinStands;
import com.theminespirit.core.mining.MiningPlanner;
import com.theminespirit.core.mining.TargetBuffer;
import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

final class HarvestSearch {
    private static final double APPROACH_ARRIVAL = 0.6;
    private static final double APPROACH_PROGRESS_STEP = 0.05;

    private final SpiritEntity spirit;
    private final HarvestSpec spec;
    private final LevelSafetyView safety;
    private final HarvestCounters counters;
    private final HarvestTunnel tunnel;
    private final Consumer<String> fail;
    private final Approach approach;
    private Supplier<HarvestSession.State> prepare;
    private Function<BlockCoord, HarvestSession.State> accept;
    private final TargetBuffer buffer = new TargetBuffer();
    private final GatherCells terrain;
    private final ApproachFailureTracker approachFailures = new ApproachFailureTracker();
    private final Set<BlockCoord> reachStandsTried = new HashSet<>();

    private BlockCoord currentTarget;
    private BlockCoord currentStand;
    private BlockCoord lastUnreachable;
    private double closestApproach = Double.MAX_VALUE;

    HarvestSearch(
            SpiritEntity spirit,
            HarvestSpec spec,
            LevelSafetyView safety,
            HarvestCounters counters,
            HarvestTunnel tunnel,
            Consumer<String> fail) {
        this.spirit = spirit;
        this.spec = spec;
        this.safety = safety;
        this.counters = counters;
        this.tunnel = tunnel;
        this.fail = fail;
        this.approach = new Approach(spirit);
        this.terrain = new GatherCells(spirit.level());
    }

    BlockCoord target() {
        return currentTarget;
    }

    void target(BlockCoord target) {
        currentTarget = target;
    }

    BlockCoord stand() {
        return currentStand;
    }

    void stand(BlockCoord stand) {
        currentStand = stand;
    }

    void clearTargets() {
        currentTarget = null;
        currentStand = null;
    }

    void offerTarget(BlockCoord target) {
        buffer.offer(List.of(target));
    }

    void returnTarget() {
        buffer.offer(List.of(currentTarget));
        clearTargets();
    }

    void noteUnreachable(BlockCoord target) {
        lastUnreachable = target;
    }

    void excludeStand(BlockCoord stand, BlockCoord target) {
        approachFailures.excludeStand(stand, target);
    }

    BlockCoord targetForReason() {
        if (currentTarget != null) {
            return currentTarget;
        }
        return lastUnreachable;
    }

    HarvestSession.State find() {
        if (spec.targets().refusalReason() != null) {
            fail.accept(spec.targets().refusalReason());
            return HarvestSession.State.FIND;
        }
        if (!buffer.needsRefill()) {
            return HarvestSession.State.PICK;
        }
        buffer.offer(spec.targets().advance());
        if (!buffer.isEmpty()) {
            return HarvestSession.State.PICK;
        }
        if (spec.targets().done()) {
            fail.accept(HarvestReasonText.stopReason(
                    counters.tally(),
                    spec.radius(),
                    spec.vein(),
                    spec.kind() == WorkKind.GATHER,
                    lastUnreachable,
                    tunnel.refusal()));
        }
        return HarvestSession.State.FIND;
    }

    HarvestSession.State pick() {
        if (buffer.isEmpty()) {
            return HarvestSession.State.FIND;
        }
        if (buffer.needsRefill() && !spec.targets().done()) {
            buffer.offer(spec.targets().advance());
        }
        BlockPos from = spirit.blockPosition();
        BlockCoord origin = new BlockCoord(from.getX(), from.getY(), from.getZ());
        List<BlockCoord> candidates = buffer.targets(origin);
        if (spec.kind() == WorkKind.GATHER) {
            candidates = terrain.shallow(candidates, buffer);
            if (candidates.isEmpty()) {
                return HarvestSession.State.FIND;
            }
        }
        for (BlockCoord candidate : candidates) {
            boolean ownColumn = candidate.x() == origin.x() && candidate.z() == origin.z();
            if (SpiritReach.reachable(origin, candidate)
                    && !ownColumn
                    && (spec.vein() || terrain.exposed(candidate))
                    && !approachFailures.excludedStands().contains(origin)) {
                HarvestSession.State blocked = accept.apply(candidate);
                if (blocked != null) {
                    return blocked;
                }
                currentStand = origin;
                return prepare.get();
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
                    return HarvestSession.State.APPROACH;
                }
                if (spec.vein() && tunnel.begin(origin, candidates)) {
                    return HarvestSession.State.TUNNEL;
                }
            }
            counters.addUnsafe(exhausted.unsafe());
            counters.recordNoStandSkips(exhausted.noStand());
            counters.addUnreachable(exhausted.unreachable());
            buffer.clearActive();
            return HarvestSession.State.FIND;
        }
        MiningPlanner.Next next = (MiningPlanner.Next) result;
        if (spec.kind() == WorkKind.GATHER && !standReachable(next.stand())) {
            counters.addUnreachable();
            lastUnreachable = next.target();
            approachFailures.excludeStand(next.stand(), next.target());
            return HarvestSession.State.PICK;
        }
        HarvestSession.State blocked = accept.apply(next.target());
        if (blocked != null) {
            return blocked;
        }
        currentStand = next.stand();
        beginApproach(
                new Vec3(next.stand().x() + 0.5, next.stand().y(), next.stand().z() + 0.5));
        return HarvestSession.State.APPROACH;
    }

    void prepareWith(Supplier<HarvestSession.State> prepare) {
        this.prepare = prepare;
    }

    void acceptWith(Function<BlockCoord, HarvestSession.State> accept) {
        this.accept = accept;
    }

    void removeBuffered(BlockCoord target) {
        buffer.remove(target);
    }

    HarvestSession.State approachTick() {
        noteApproachProgress();
        return switch (approach.tick()) {
            case MOVING -> HarvestSession.State.APPROACH;
            case ARRIVED -> currentTarget == null ? HarvestSession.State.PICK : prepare.get();
            case STUCK, TIMEOUT -> stuck();
        };
    }

    private HarvestSession.State stuck() {
        if (currentTarget == null) {
            currentStand = null;
            return HarvestSession.State.PICK;
        }
        if (approachFailures.recordFailure(currentStand, currentTarget)) {
            counters.addUnreachable();
        } else {
            buffer.offer(List.of(currentTarget));
        }
        clearTargets();
        return HarvestSession.State.PICK;
    }

    private boolean approachReachStand(BlockCoord origin, List<BlockCoord> candidates) {
        List<BlockCoord> reachable = spec.vein()
                ? candidates
                : candidates.stream().filter(terrain::exposed).toList();
        Optional<BlockCoord> stand =
                MineVeinStands.next(reachable, origin, safety, approachFailures.excludedStands(), reachStandsTried);
        if (stand.isEmpty()) {
            counters.addUnreachable();
            if (!reachable.isEmpty()) {
                lastUnreachable = reachable.get(0);
            }
            return false;
        }
        if (spec.kind() == WorkKind.GATHER && !standReachable(stand.get())) {
            counters.addUnreachable();
            lastUnreachable = reachable.isEmpty() ? null : reachable.get(0);
            return false;
        }
        reachStandsTried.add(stand.get());
        currentTarget = null;
        currentStand = stand.get();
        beginApproach(new Vec3(currentStand.x() + 0.5, currentStand.y(), currentStand.z() + 0.5));
        return true;
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
            counters.progressWatchdog();
        }
    }

    void cancelApproach() {
        approach.cancel();
    }

    void suspendApproach() {
        approach.suspend();
    }
}
