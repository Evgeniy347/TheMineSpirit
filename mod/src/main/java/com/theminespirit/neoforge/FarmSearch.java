package com.theminespirit.neoforge;

import com.theminespirit.core.building.SpiritReach;
import com.theminespirit.core.farming.CropKind;
import com.theminespirit.core.farming.FarmJob;
import com.theminespirit.core.farming.FarmPlanner;
import com.theminespirit.core.farming.FarmPlot;
import com.theminespirit.core.mining.TargetBuffer;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

final class FarmSearch {
    enum ApproachStep {
        MOVING,
        ARRIVED,
        LOST
    }

    private static final double APPROACH_ARRIVAL = 0.6;
    private static final double GROUND_PROBE = 0.2;

    private final SpiritEntity spirit;
    private final FarmJob job;
    private final CropKind crop;
    private final HarvestTargets targets;
    private final LevelFarmView view;
    private final LevelSafetyView safety;
    private final FarmCounters counters;
    private final Consumer<String> fail;
    private final Supplier<FarmSession.State> exhausted;
    private final Approach approach;
    private final TargetBuffer buffer = new TargetBuffer();

    private BlockCoord currentGround;

    FarmSearch(
            SpiritEntity spirit,
            FarmJob job,
            CropKind crop,
            HarvestTargets targets,
            LevelFarmView view,
            FarmCounters counters,
            Consumer<String> fail,
            Supplier<FarmSession.State> exhausted) {
        this.spirit = spirit;
        this.job = job;
        this.crop = crop;
        this.targets = targets;
        this.view = view;
        this.safety = new LevelSafetyView(spirit.level());
        this.counters = counters;
        this.fail = fail;
        this.exhausted = exhausted;
        this.approach = new Approach(spirit);
    }

    BlockCoord ground() {
        return currentGround;
    }

    void restoreGround(BlockCoord ground) {
        currentGround = ground;
    }

    void clearGround() {
        currentGround = null;
    }

    void reoffer(BlockCoord ground) {
        buffer.offer(List.of(ground));
    }

    FarmSession.State find() {
        if (targets.refusalReason() != null) {
            fail.accept(targets.refusalReason());
            return FarmSession.State.FIND;
        }
        if (!buffer.needsRefill()) {
            return FarmSession.State.PICK;
        }
        buffer.offer(targets.advance());
        if (targets.refusalReason() != null) {
            fail.accept(targets.refusalReason());
            return FarmSession.State.FIND;
        }
        if (!buffer.isEmpty()) {
            return FarmSession.State.PICK;
        }
        if (targets.done()) {
            return exhausted.get();
        }
        return FarmSession.State.FIND;
    }

    FarmSession.State pick() {
        if (buffer.isEmpty()) {
            return FarmSession.State.FIND;
        }
        if (buffer.needsRefill() && !targets.done()) {
            buffer.offer(targets.advance());
        }
        counters.planCall();
        BlockCoord origin = coordOf(spirit.blockPosition());
        FarmPlanner.Outcome result =
                FarmPlanner.next(job, crop, origin, buffer.targets(origin), view, safety, targets.hydration());
        if (result instanceof FarmPlanner.Exhausted exhaustedPlan) {
            counters.addIneligible(exhaustedPlan.ineligible());
            counters.addDry(exhaustedPlan.dry());
            counters.addNoStand(exhaustedPlan.noStand());
            buffer.clearActive();
            return FarmSession.State.FIND;
        }
        FarmPlanner.Next next = (FarmPlanner.Next) result;
        buffer.remove(next.target());
        currentGround = next.target();
        if (SpiritReach.reachable(origin, currentGround)) {
            return FarmSession.State.ACT;
        }
        approach.begin(
                new Vec3(next.stand().x() + 0.5, next.stand().y(), next.stand().z() + 0.5), APPROACH_ARRIVAL);
        return FarmSession.State.APPROACH;
    }

    ApproachStep approachTick() {
        return switch (approach.tick()) {
            case MOVING -> ApproachStep.MOVING;
            case ARRIVED -> ApproachStep.ARRIVED;
            case STUCK, TIMEOUT -> lost();
        };
    }

    private ApproachStep lost() {
        if (inReachOfTarget()) {
            return ApproachStep.ARRIVED;
        }
        counters.addNoStand();
        return ApproachStep.LOST;
    }

    private boolean inReachOfTarget() {
        return SpiritReach.reachable(coordOf(spirit.blockPosition()), currentGround);
    }

    void cancelApproach() {
        approach.cancel();
    }

    static BlockCoord coordOf(BlockPos pos) {
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }

    static BlockPos posOf(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }

    static int plotGroundY(SpiritEntity spirit) {
        return (int) Math.floor(spirit.getY() - GROUND_PROBE);
    }

    static BlockPos plotPos(FarmPlot plot, int groundY) {
        BlockCoord center = plot.center(groundY);
        return new BlockPos(center.x(), center.y(), center.z());
    }
}
