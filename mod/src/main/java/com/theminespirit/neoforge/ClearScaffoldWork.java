package com.theminespirit.neoforge;

import com.theminespirit.core.mining.ClearOrder;
import com.theminespirit.core.mining.ClearScaffold;
import com.theminespirit.core.mining.MiningTuning;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.board.PlanWalkways;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

final class ClearScaffoldWork {
    enum Result {
        IDLE,
        RUNNING,
        FAILED,
        DONE
    }

    private static final int COLUMN_REACH = 2;
    private static final double SPOT_ARRIVAL = 0.6;
    private final SpiritEntity spirit;
    private final ClearAreaSession session;
    private final ScaffoldReach reach;
    private boolean descendTried;
    private boolean relocated;
    private boolean moving;

    ClearScaffoldWork(SpiritEntity spirit, ClearAreaSession session) {
        this.spirit = spirit;
        this.session = session;
        this.reach = new ScaffoldReach(spirit, "высокая клетка");
    }

    Result tick() {
        if (moving) {
            if (session.approach.tick() == Approach.State.MOVING) {
                return Result.RUNNING;
            }
            moving = false;
        }
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
        List<BlockCoord> work = session.targets.work(session.remaining);
        return ClearScaffold.step(
                new BlockCoord(feet.getX(), feet.getY(), feet.getZ()),
                ClearOrder.topmost(work, cell -> true),
                MiningTuning.MAX_STAND_RISE,
                PillarDownScript.ownBelow(spirit),
                COLUMN_REACH);
    }

    private Result climb(int rise) {
        if (!relocated && relocate()) {
            return Result.RUNNING;
        }
        ScaffoldReach.Result started = reach.climb(rise);
        if (started == ScaffoldReach.Result.FAILED) {
            session.fail(reach.failureReason());
            return Result.FAILED;
        }
        return Result.RUNNING;
    }

    private boolean relocate() {
        relocated = true;
        BlockCoord top = session.targets.highestWork(session.remaining);
        if (top == null || !(spirit.level() instanceof ServerLevel level)) {
            return false;
        }
        BlockPos feet = spirit.blockPosition();
        Optional<BlockCoord> spot = ClearScaffold.pillarSpot(
                new BlockCoord(feet.getX(), feet.getY(), feet.getZ()),
                top,
                cell -> PlanWalkways.on(level, cell.x(), cell.z()),
                cell -> standable(level, new BlockPos(cell.x(), cell.y(), cell.z())),
                COLUMN_REACH);
        if (spot.isEmpty()) {
            return false;
        }
        BlockCoord at = spot.orElseThrow();
        session.approach.beginWithRoutes(new Vec3(at.x() + 0.5, at.y(), at.z() + 0.5), SPOT_ARRIVAL);
        moving = true;
        return true;
    }

    private static boolean standable(ServerLevel level, BlockPos at) {
        return level.getBlockState(at).isAir()
                && level.getBlockState(at.above()).isAir()
                && level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP);
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
