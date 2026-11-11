package com.theminespirit.neoforge;

import com.theminespirit.core.board.lease.RollbackReach;
import com.theminespirit.core.building.SpiritReach;
import com.theminespirit.core.engine.FailureFact;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
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
    private final StandWalk stands;

    private @Nullable ReachApproach walk;
    private @Nullable BlockCoord walkingTo;
    private @Nullable BlockCoord walkRefusedFor;
    private String walkRefusal;
    private @Nullable BlockCoord climbCell;
    private @Nullable BlockCoord climbedFor;
    private @Nullable TunnelReach tunnel;
    private @Nullable BlockCoord tunnelFor;
    private @Nullable BlockCoord tunnelAt;
    private boolean retractReady;
    private String failureReason;
    private @Nullable FailureFact failureFact;

    LeaseReach(SpiritEntity spirit, LevelSafetyView safety) {
        this.spirit = spirit;
        this.safety = safety;
        this.scaffold = new ScaffoldReach(spirit, "откат аренды");
        this.stands = new StandWalk(spirit);
    }

    ScaffoldReach scaffold() {
        return scaffold;
    }

    boolean busy() {
        return retractReady
                || walkingTo != null
                || stands.busy()
                || scaffold.busy()
                || (tunnel != null && tunnelFor != null);
    }

    Outcome tick(BlockCoord cell) {
        if (retractReady) {
            return retractTick();
        }
        if (stands.busy()) {
            return tickStand();
        }
        if (walkingTo != null) {
            Outcome outcome = tickWalk();
            return walkingTo == null ? outcome : Outcome.RUNNING;
        }
        if (scaffold.busy()) {
            ScaffoldReach.Result result = scaffold.tick();
            if (result == ScaffoldReach.Result.FAILED) {
                return failed(scaffold.failureReason(), scaffold.failureFact());
            }
            return result == ScaffoldReach.Result.RUNNING ? Outcome.RUNNING : Outcome.READY;
        }
        if (tunnel != null && tunnelFor != null) {
            return forwardTunnel(cell);
        }
        if (cell.y() <= feet().y() && PillarDownScript.ownBelow(spirit)) {
            return scaffoldOutcome(scaffold.stepDown());
        }
        if (cell.equals(walkRefusedFor) && walkRefusal != null) {
            return fallback(cell);
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

    @Nullable
    FailureFact failureFact() {
        return failureFact;
    }

    void cancel() {
        if (walk != null) {
            walk.cancel();
        }
        stands.cancel();
        scaffold.cancel();
        if (tunnel != null) {
            tunnel.cancel();
        }
    }

    private Outcome fallback(BlockCoord cell) {
        int rise = cell.y() - feet().y();
        RollbackReach.Step action = RollbackReach.next(rise, PillarDownScript.ownBelow(spirit));
        return switch (action.action()) {
            case CLIMB -> climb(cell);
            case STEP_DOWN -> stepDown();
            case DIG -> dig(cell);
        };
    }

    private Outcome climb(BlockCoord cell) {
        if (cell.equals(climbedFor)) {
            return failed("к клетке " + cell + " столб уже строили, а она не в досягаемости: откат ждёт пути");
        }
        if (!beside(cell)) {
            List<BlockCoord> stand = stands.stands(cell);
            if (stand.isEmpty()) {
                return failed("к клетке " + cell + " нет стоянки сбоку для столба: откат ждёт пути");
            }
            climbCell = cell;
            stands.begin(stand);
            return Outcome.RUNNING;
        }
        walkRefusedFor = null;
        walkRefusal = null;
        int rise = cell.y() - feet().y();
        if (rise < 1) {
            return failed("к клетке " + cell + " не подняться со стоянки " + feet() + ": столб не нужен");
        }
        climbedFor = cell;
        return scaffoldOutcome(scaffold.climb(rise));
    }

    private Outcome stepDown() {
        return scaffoldOutcome(scaffold.stepDown());
    }

    private Outcome scaffoldOutcome(ScaffoldReach.Result started) {
        if (started == ScaffoldReach.Result.FAILED) {
            return failed(scaffold.failureReason(), scaffold.failureFact());
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
            return failed("подход копкой к " + tunnelAt + ": " + current.failureReason(), current.failureFact());
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
            return failed("разбор прохода к " + tunnelAt + ": " + current.failureReason(), current.failureFact());
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
            BlockCoord failedTo = walkingTo;
            walkRefusal = current.refusal() == null ? "путь не найден" : current.refusal();
            walkingTo = null;
            current.cancel();
            walkRefusedFor = failedTo;
            return Outcome.READY;
        }
        walkingTo = null;
        walkRefusedFor = null;
        walkRefusal = null;
        return Outcome.READY;
    }

    private Outcome tickStand() {
        Approach.State state = stands.tick();
        if (state == Approach.State.MOVING) {
            return Outcome.RUNNING;
        }
        BlockCoord target = climbCell;
        climbCell = null;
        if (state == Approach.State.STUCK || state == Approach.State.TIMEOUT) {
            String where = target == null ? "" : " у клетки " + target;
            return failed("к стоянке" + where + " не подойти: " + stands.refusal());
        }
        if (target == null) {
            return Outcome.READY;
        }
        int rise = target.y() - feet().y();
        if (rise < 1) {
            return failed("к клетке " + target + " не подняться со стоянки " + feet() + ": столб не нужен");
        }
        climbedFor = target;
        return scaffoldOutcome(scaffold.climb(rise));
    }

    private Outcome failed(String reason) {
        return failed(reason, null);
    }

    private Outcome failed(String reason, @Nullable FailureFact fact) {
        failureReason = reason;
        failureFact = fact;
        return Outcome.FAILED;
    }

    private boolean beside(BlockCoord cell) {
        int dx = Math.abs(spirit.blockPosition().getX() - cell.x());
        int dz = Math.abs(spirit.blockPosition().getZ() - cell.z());
        int reach = SpiritReach.current().horizontal();
        return (dx > 0 || dz > 0) && dx <= reach && dz <= reach;
    }

    private BlockCoord feet() {
        BlockPos feet = spirit.blockPosition();
        return new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
    }

    private static BlockPos pos(BlockCoord cell) {
        return new BlockPos(cell.x(), cell.y(), cell.z());
    }
}
