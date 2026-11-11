package com.theminespirit.neoforge;

import com.theminespirit.core.board.lease.RollbackReach;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.board.GraphTargets;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

final class StandWalk {
    static final int RADIUS = 3;
    private static final double ARRIVAL = 0.6;

    private final SpiritEntity spirit;
    private final ServerLevel level;
    private @Nullable Approach approach;
    private @Nullable BlockCoord to;
    private String refusal;

    StandWalk(SpiritEntity spirit) {
        this.spirit = spirit;
        this.level = (ServerLevel) spirit.level();
    }

    Optional<BlockCoord> stand(BlockCoord cell) {
        return RollbackReach.climbStand(cell, GraphTargets.rollbackGround(level), RADIUS)
                .filter(candidate -> candidate.x() != cell.x() || candidate.z() != cell.z());
    }

    boolean busy() {
        return to != null;
    }

    void begin(BlockCoord cell) {
        if (approach == null) {
            approach = new Approach(spirit);
        } else {
            approach.cancel();
        }
        approach.begin(new Vec3(cell.x() + 0.5, cell.y(), cell.z() + 0.5), ARRIVAL);
        to = cell;
        refusal = null;
    }

    Approach.State tick() {
        Approach current = approach;
        if (current == null) {
            to = null;
            return Approach.State.ARRIVED;
        }
        Approach.State state = current.tick();
        if (state != Approach.State.MOVING) {
            refusal = current.refusal();
            to = null;
            approach = null;
        }
        return state;
    }

    String refusal() {
        return refusal == null ? "путь не найден" : refusal;
    }

    void cancel() {
        if (approach != null) {
            approach.cancel();
            approach = null;
        }
        to = null;
    }
}
