package com.theminespirit.neoforge;

import com.theminespirit.core.observation.BlockCoord;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

final class LeaseWalk {
    private final SpiritEntity spirit;

    private @Nullable ReachApproach walk;
    private @Nullable BlockCoord walkingTo;
    private @Nullable BlockCoord refusedFor;
    private String refusal;

    LeaseWalk(SpiritEntity spirit) {
        this.spirit = spirit;
    }

    boolean busy() {
        return walkingTo != null;
    }

    void begin(BlockCoord cell) {
        if (walk == null) {
            walk = new ReachApproach(spirit);
        } else {
            walk.cancel();
        }
        walk.begin(new BlockPos(cell.x(), cell.y(), cell.z()));
        walkingTo = cell;
        refusedFor = null;
        refusal = null;
    }

    LeaseReach.Outcome tick() {
        ReachApproach current = walk;
        if (current == null) {
            walkingTo = null;
            return LeaseReach.Outcome.READY;
        }
        Approach.State state = current.tick();
        if (state == Approach.State.MOVING) {
            return LeaseReach.Outcome.RUNNING;
        }
        if (state == Approach.State.STUCK || state == Approach.State.TIMEOUT) {
            BlockCoord failedTo = walkingTo;
            refusal = current.refusal() == null ? "путь не найден" : current.refusal();
            walkingTo = null;
            current.cancel();
            refusedFor = failedTo;
            return LeaseReach.Outcome.READY;
        }
        walkingTo = null;
        refusedFor = null;
        refusal = null;
        return LeaseReach.Outcome.READY;
    }

    boolean refused(BlockCoord cell) {
        return cell.equals(refusedFor) && refusal != null;
    }

    void clearRefusal() {
        refusedFor = null;
        refusal = null;
    }

    void cancel() {
        if (walk != null) {
            walk.cancel();
        }
    }
}
