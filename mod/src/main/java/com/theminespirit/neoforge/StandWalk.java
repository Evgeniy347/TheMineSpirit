package com.theminespirit.neoforge;

import com.theminespirit.core.board.lease.RollbackReach;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.board.GraphTargets;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

final class StandWalk {
    static final int RADIUS = 3;
    private static final int MAX_STANDS = 8;
    private static final double ARRIVAL = 0.6;

    private final SpiritEntity spirit;
    private final ServerLevel level;
    private @Nullable ReachApproach reach;
    private @Nullable Approach exact;
    private List<BlockCoord> stands = List.of();
    private int next;
    private @Nullable BlockCoord to;
    private boolean precise;
    private String refusal;

    StandWalk(SpiritEntity spirit) {
        this.spirit = spirit;
        this.level = (ServerLevel) spirit.level();
    }

    List<BlockCoord> stands(BlockCoord cell) {
        return RollbackReach.climbStands(cell, GraphTargets.rollbackGround(level), RADIUS).stream()
                .filter(candidate -> candidate.x() != cell.x() || candidate.z() != cell.z())
                .limit(MAX_STANDS)
                .toList();
    }

    boolean busy() {
        return to != null;
    }

    void begin(List<BlockCoord> candidates) {
        stands = List.copyOf(candidates);
        next = 0;
        refusal = null;
        reach = ReachApproach.withRoutes(spirit);
        startNext();
    }

    Approach.State tick() {
        BlockCoord stand = to;
        if (stand == null) {
            return Approach.State.ARRIVED;
        }
        if (!precise) {
            Approach.State state = reach == null ? Approach.State.ARRIVED : reach.tick();
            if (state == Approach.State.MOVING) {
                return state;
            }
            if (state == Approach.State.ARRIVED) {
                precise = true;
                exact = new Approach(spirit);
                exact.begin(new Vec3(stand.x() + 0.5, stand.y(), stand.z() + 0.5), ARRIVAL);
                return Approach.State.MOVING;
            }
            refusal = reach == null ? null : reach.refusal();
            cancelCurrent();
            return nextStand();
        }
        Approach current = exact;
        if (current == null) {
            return Approach.State.ARRIVED;
        }
        Approach.State state = current.tick();
        if (state == Approach.State.MOVING) {
            return state;
        }
        refusal = current.refusal();
        current.cancel();
        exact = null;
        if (state == Approach.State.ARRIVED) {
            to = null;
            return state;
        }
        cancelCurrent();
        return nextStand();
    }

    String refusal() {
        return refusal == null ? "путь не найден" : refusal;
    }

    void cancel() {
        cancelCurrent();
        stands = List.of();
        next = 0;
        to = null;
    }

    private Approach.State nextStand() {
        if (next < stands.size()) {
            precise = false;
            reach = ReachApproach.withRoutes(spirit);
            startNext();
            return Approach.State.MOVING;
        }
        to = null;
        return Approach.State.STUCK;
    }

    private void startNext() {
        BlockCoord stand = stands.get(next++);
        to = stand;
        precise = false;
        if (reach != null) {
            reach.begin(new BlockPos(stand.x(), stand.y(), stand.z()));
        }
    }

    private void cancelCurrent() {
        if (reach != null) {
            reach.cancel();
            reach = null;
        }
        if (exact != null) {
            exact.cancel();
            exact = null;
        }
        to = null;
    }
}
