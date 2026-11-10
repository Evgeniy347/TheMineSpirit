package com.theminespirit.neoforge;

import com.theminespirit.core.board.lease.RollbackReach;
import com.theminespirit.core.building.SpiritReach;
import com.theminespirit.core.observation.BlockCoord;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

final class LeaseReach {
    enum Outcome {
        READY,
        RUNNING,
        FAILED
    }

    private static final int TUNNEL_STEPS = 16;

    private final SpiritEntity spirit;
    private final LevelSafetyView safety;
    private final ScaffoldReach scaffold;

    private @Nullable ReachApproach walk;
    private @Nullable BlockCoord walkingTo;
    private @Nullable BlockCoord walkRefusedFor;
    private String walkRefusal;
    private @Nullable TunnelReach tunnel;
    private @Nullable BlockCoord tunnelFor;
    private @Nullable BlockCoord tunnelAt;
    private boolean retractReady;
    private int climbAfterWalk = -1;
    private String failureReason;

    LeaseReach(SpiritEntity spirit, LevelSafetyView safety) {
        this.spirit = spirit;
        this.safety = safety;
        this.scaffold = new ScaffoldReach(spirit, "откат аренды");
    }

    ScaffoldReach scaffold() {
        return scaffold;
    }

    boolean busy() {
        return retractReady || walkingTo != null || scaffold.busy() || (tunnel != null && tunnelFor != null);
    }

    Outcome tick(BlockCoord cell, BlockCoord base) {
        if (retractReady) {
            return retractTick();
        }
        if (walkingTo != null) {
            Outcome outcome = tickWalk();
            return walkingTo == null ? outcome : Outcome.RUNNING;
        }
        if (scaffold.busy()) {
            ScaffoldReach.Result result = scaffold.tick();
            if (result == ScaffoldReach.Result.FAILED) {
                return failed(scaffold.failureReason());
            }
            return result == ScaffoldReach.Result.RUNNING ? Outcome.RUNNING : Outcome.READY;
        }
        if (tunnel != null && tunnelFor != null) {
            return forwardTunnel(cell);
        }
        if (cell.equals(walkRefusedFor) && walkRefusal != null) {
            return fallback(cell, base);
        }
        startWalk(cell);
        return Outcome.RUNNING;
    }

    void completed(BlockCoord cell) {
        if (tunnel != null && cell.equals(tunnelAt)) {
            retractReady = true;
        }
    }

    String failureReason() {
        return failureReason == null ? "" : failureReason;
    }

    void cancel() {
        if (walk != null) {
            walk.cancel();
        }
        scaffold.cancel();
        if (tunnel != null) {
            tunnel.cancel();
        }
    }

    private Outcome fallback(BlockCoord cell, BlockCoord base) {
        int rise = cell.y() - feet().y();
        RollbackReach.Step action = RollbackReach.next(rise, PillarDownScript.ownBelow(spirit));
        return switch (action.action()) {
            case CLIMB -> climb(rise, base);
            case STEP_DOWN -> stepDown();
            case DIG -> dig(cell);
        };
    }

    private Outcome climb(int rise, BlockCoord base) {
        if (!near(base) && !base.equals(walkRefusedFor)) {
            startWalk(base);
            climbAfterWalk = rise;
            return Outcome.RUNNING;
        }
        walkRefusal = null;
        return scaffoldOutcome(scaffold.climb(rise));
    }

    private Outcome stepDown() {
        return scaffoldOutcome(scaffold.stepDown());
    }

    private Outcome scaffoldOutcome(ScaffoldReach.Result started) {
        if (started == ScaffoldReach.Result.FAILED) {
            return failed(scaffold.failureReason());
        }
        return Outcome.RUNNING;
    }

    private Outcome dig(BlockCoord cell) {
        if (tunnel != null) {
            return failed("к клетке " + cell + " уже проложен проход, а она не достигнута");
        }
        TunnelReach started = TunnelReach.begin(spirit, cell, feet(), safety, TUNNEL_STEPS);
        if (started == null) {
            return failed("к клетке " + cell + " нет подхода и прохода: откат ждёт пути");
        }
        tunnel = started;
        tunnelFor = cell;
        tunnelAt = cell;
        walkRefusal = null;
        return Outcome.RUNNING;
    }

    private Outcome forwardTunnel(BlockCoord cell) {
        TunnelReach current = tunnel;
        if (current == null) {
            return Outcome.READY;
        }
        TunnelReach.State state = current.tick();
        if (state == TunnelReach.State.FAILED) {
            return failed("подход копкой к " + tunnelAt + ": " + current.failureReason());
        }
        if (state != TunnelReach.State.ARRIVED) {
            return Outcome.RUNNING;
        }
        tunnelFor = null;
        return SpiritReach.reachable(feet(), cell) ? Outcome.READY : failed("проход не довёл до клетки " + cell);
    }

    private Outcome retractTick() {
        TunnelReach current = tunnel;
        if (current == null) {
            retractReady = false;
            return Outcome.READY;
        }
        TunnelReach.State state = current.retract();
        if (state == TunnelReach.State.FAILED) {
            return failed("разбор прохода к " + tunnelAt + ": " + current.failureReason());
        }
        if (state == TunnelReach.State.RUNNING) {
            return Outcome.RUNNING;
        }
        tunnel = null;
        tunnelAt = null;
        retractReady = false;
        return Outcome.READY;
    }

    private void startWalk(BlockCoord cell) {
        if (walk == null) {
            walk = new ReachApproach(spirit);
        } else {
            walk.cancel();
        }
        walk.begin(pos(cell));
        walkingTo = cell;
        walkRefusedFor = null;
        walkRefusal = null;
        climbAfterWalk = -1;
    }

    private Outcome tickWalk() {
        ReachApproach current = walk;
        if (current == null) {
            walkingTo = null;
            return Outcome.READY;
        }
        Approach.State state = current.tick();
        if (state == Approach.State.MOVING) {
            return Outcome.RUNNING;
        }
        if (state == Approach.State.STUCK || state == Approach.State.TIMEOUT) {
            walkRefusedFor = walkingTo;
            walkRefusal = current.refusal() == null ? "путь не найден" : current.refusal();
            walkingTo = null;
            current.cancel();
            return Outcome.READY;
        }
        walkingTo = null;
        walkRefusedFor = null;
        walkRefusal = null;
        if (climbAfterWalk > 0) {
            int rise = climbAfterWalk;
            climbAfterWalk = -1;
            return scaffoldOutcome(scaffold.climb(rise));
        }
        return Outcome.READY;
    }

    private Outcome failed(String reason) {
        failureReason = reason;
        return Outcome.FAILED;
    }

    private boolean near(BlockCoord cell) {
        BlockPos feet = spirit.blockPosition();
        return Math.abs(feet.getX() - cell.x()) <= 2
                && Math.abs(feet.getZ() - cell.z()) <= 2
                && Math.abs(feet.getY() - cell.y()) <= 2;
    }

    private BlockCoord feet() {
        BlockPos feet = spirit.blockPosition();
        return new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
    }

    private static BlockPos pos(BlockCoord cell) {
        return new BlockPos(cell.x(), cell.y(), cell.z());
    }
}
