package com.theminespirit.neoforge;

import com.theminespirit.core.mining.ClearScaffold;
import com.theminespirit.core.mining.MiningTuning;
import com.theminespirit.core.observation.BlockCoord;
import net.minecraft.core.BlockPos;

final class ClearScaffoldWork {
    enum Result {
        IDLE,
        RUNNING,
        FAILED,
        DONE
    }

    private final SpiritEntity spirit;
    private final ClearAreaSession session;
    private final ScaffoldReach reach;
    private boolean descendTried;

    ClearScaffoldWork(SpiritEntity spirit, ClearAreaSession session) {
        this.spirit = spirit;
        this.session = session;
        this.reach = new ScaffoldReach(spirit, "высокая клетка");
    }

    Result tick() {
        ScaffoldReach.Result work = reach.tick();
        if (work == ScaffoldReach.Result.RUNNING) {
            return Result.RUNNING;
        }
        if (work == ScaffoldReach.Result.FAILED) {
            session.fail(reach.failureReason());
            return Result.FAILED;
        }
        if (session.outcome() == ClearAreaSession.Outcome.FAILED) {
            reach.cancel();
            return Result.FAILED;
        }
        if (session.outcome() == ClearAreaSession.Outcome.SUCCEEDED) {
            return finish();
        }
        if (!session.picking()) {
            return Result.IDLE;
        }
        ClearScaffold.Decision decision = decide();
        return switch (decision.action()) {
            case CLIMB -> climb(decision.height());
            case DESCEND -> stepDown();
            default -> Result.IDLE;
        };
    }

    void cancel() {
        reach.cancel();
    }

    private ClearScaffold.Decision decide() {
        BlockPos feet = spirit.blockPosition();
        ClearScaffold.Decision decision = ClearScaffold.decide(
                new BlockCoord(feet.getX(), feet.getY(), feet.getZ()),
                session.targets.highestWork(session.remaining),
                MiningTuning.MAX_STAND_RISE,
                PillarDownScript.ownBelow(spirit));
        if (decision.action() == ClearScaffold.Action.CLIMB && reach.climbed()) {
            return new ClearScaffold.Decision(ClearScaffold.Action.WORK, 0);
        }
        return decision;
    }

    private Result climb(int rise) {
        ScaffoldReach.Result started = reach.climb(rise);
        if (started == ScaffoldReach.Result.FAILED) {
            session.fail(reach.failureReason());
            return Result.FAILED;
        }
        return Result.RUNNING;
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
