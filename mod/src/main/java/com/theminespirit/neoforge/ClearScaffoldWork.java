package com.theminespirit.neoforge;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

final class ClearScaffoldWork {
    enum Result {
        IDLE,
        RUNNING,
        FAILED,
        DONE
    }

    private static final double SPOT_ARRIVAL = 0.25;
    private final SpiritEntity spirit;
    private final ClearAreaSession session;
    private ScaffoldReach reach;
    private final Set<ScaffoldAccess.Plan> tried = new HashSet<>();
    private ScaffoldAccess.@Nullable Plan pending;
    private boolean descendTried;
    private boolean preparing;
    private boolean moving;

    ClearScaffoldWork(SpiritEntity spirit, ClearAreaSession session) {
        this.spirit = spirit;
        this.session = session;
        this.reach = new ScaffoldReach(spirit, "высокая клетка");
    }

    Result tick() {
        if (session.outcome() == ClearAreaSession.Outcome.FAILED) {
            cancel();
            return Result.FAILED;
        }
        if (moving) {
            return approach();
        }
        ScaffoldReach.Result work = reach.tick();
        if (work == ScaffoldReach.Result.DONE && reach.climbed()) {
            work = reach.settle();
        }
        if (work == ScaffoldReach.Result.RUNNING) {
            return Result.RUNNING;
        }
        if (work == ScaffoldReach.Result.FAILED) {
            cancel();
            session.fail(reach.failureReason());
            return Result.FAILED;
        }
        ScaffoldAccess.Plan plan = pending;
        if (preparing && plan != null) {
            preparing = false;
            BlockCoord at = plan.base();
            session.approach.beginWithRoutes(new Vec3(at.x() + 0.5, at.y(), at.z() + 0.5), SPOT_ARRIVAL);
            moving = true;
            return Result.RUNNING;
        }
        releaseBase();
        if (session.outcome() == ClearAreaSession.Outcome.SUCCEEDED) {
            return finish();
        }
        if (session.picking() && PillarDownScript.ownBelow(spirit)) {
            List<BlockCoord> cells = session.targets.work(session.remaining);
            if (!cells.isEmpty() && cells.stream().allMatch(cell -> cell.y() < spirit.getBlockY())) {
                return stepDown();
            }
        }
        return Result.IDLE;
    }

    boolean request(List<BlockCoord> cells) {
        ScaffoldAccess.Plan plan = ScaffoldAccess.find(session, session.targets.work(cells), tried);
        if (plan == null) {
            return false;
        }
        if (reach.climbed()
                && PillarDownScript.ownBelow(spirit)
                && !spirit.blockPosition().equals(ClearAreaSession.toPos(plan.base()))) {
            descendTried = true;
            if (reach.descend() == ScaffoldReach.Result.FAILED) {
                session.fail(reach.failureReason());
            }
            return true;
        }
        if (reach.climbed() && !PillarDownScript.ownBelow(spirit)) {
            reach.cancel();
            reach = new ScaffoldReach(spirit, "высокая клетка");
            descendTried = false;
        }
        tried.add(plan);
        pending = plan;
        session.reservations.reserve(plan.base(), spirit.getUUID());
        preparing = true;
        if (reach.prepare(plan.height()) == ScaffoldReach.Result.FAILED) {
            session.fail(reach.failureReason());
        }
        return true;
    }

    void suspend() {
        reach.suspend();
    }

    void cancel() {
        reach.cancel();
        session.approach.cancel();
        releaseBase();
        moving = false;
        preparing = false;
    }

    private Result approach() {
        Approach.State state = session.approach.tick();
        if (state == Approach.State.MOVING) {
            return Result.RUNNING;
        }
        moving = false;
        ScaffoldAccess.Plan plan = pending;
        if (state != Approach.State.ARRIVED
                || plan == null
                || !spirit.blockPosition().equals(ClearAreaSession.toPos(plan.base()))
                || !spirit.onGround()
                || !ScaffoldAccess.safe(session, plan)) {
            session.approach.cancel();
            releaseBase();
            return Result.IDLE;
        }
        if (PillarColumn.count(spirit, "minecraft:dirt") < plan.height()) {
            preparing = true;
            if (reach.prepare(plan.height()) == ScaffoldReach.Result.FAILED) {
                session.fail(reach.failureReason());
                return Result.FAILED;
            }
            return Result.RUNNING;
        }
        if (reach.climb(plan.height()) == ScaffoldReach.Result.FAILED) {
            session.fail(reach.failureReason());
            return Result.FAILED;
        }
        return Result.RUNNING;
    }

    private void releaseBase() {
        if (pending != null) {
            session.reservations.release(pending.base(), spirit.getUUID());
            pending = null;
        }
    }

    private Result stepDown() {
        ScaffoldReach.Result started = reach.stepDown();
        if (started == ScaffoldReach.Result.FAILED) {
            session.fail(reach.failureReason());
            return Result.FAILED;
        }
        return started == ScaffoldReach.Result.RUNNING ? Result.RUNNING : Result.IDLE;
    }

    private Result finish() {
        if (!reach.climbed() || descendTried) {
            return Result.DONE;
        }
        descendTried = true;
        ScaffoldReach.Result down = reach.descend();
        if (down == ScaffoldReach.Result.FAILED) {
            session.fail(reach.failureReason());
            return Result.FAILED;
        }
        return down == ScaffoldReach.Result.RUNNING ? Result.RUNNING : Result.DONE;
    }
}
