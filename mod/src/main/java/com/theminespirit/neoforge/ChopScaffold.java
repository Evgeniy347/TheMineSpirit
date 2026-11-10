package com.theminespirit.neoforge;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.lease.LeaseRun;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

final class ChopScaffold {
    enum Climb {
        RUNNING,
        BUILT,
        FAILED
    }

    enum Descent {
        RUNNING,
        DONE,
        FAILED
    }

    private final SpiritEntity spirit;
    private @Nullable ScaffoldClimb climb;
    private @Nullable LeaseRun lease;
    private List<BlockCoord> cells = List.of();
    private @Nullable PillarDismantler dismantler;
    private String failureReason = "";

    ChopScaffold(SpiritEntity spirit) {
        this.spirit = spirit;
    }

    boolean begin(BlockCoord base, int standY) {
        int rise = standY - base.y();
        if (rise < 1) {
            return true;
        }
        if (PillarColumn.cheapBlock(spirit) == null) {
            failureReason = "нет земли или булыжника на подмости";
            return false;
        }
        climb = new ScaffoldClimb(spirit, rise, new BlockPos(base.x(), base.y(), base.z()));
        return true;
    }

    boolean climbing() {
        return climb != null;
    }

    Climb tickClimb() {
        ScaffoldClimb runner = climb;
        if (runner == null) {
            return Climb.FAILED;
        }
        return switch (runner.tick()) {
            case RUNNING -> Climb.RUNNING;
            case BUILT -> {
                lease = runner.lease();
                cells = runner.cells();
                climb = null;
                yield Climb.BUILT;
            }
            case FAILED -> {
                failureReason = runner.failureReason();
                climb = null;
                yield Climb.FAILED;
            }
        };
    }

    boolean hasScaffold() {
        return !cells.isEmpty();
    }

    boolean descending() {
        return dismantler != null;
    }

    void startDescent() {
        List<BlockCoord> order = new ArrayList<>(cells);
        Collections.reverse(order);
        dismantler = new PillarDismantler(spirit, order, lease);
    }

    Descent tickDescent() {
        PillarDismantler runner = dismantler;
        if (runner == null) {
            return Descent.DONE;
        }
        return switch (runner.tick()) {
            case RUNNING -> Descent.RUNNING;
            case DONE -> {
                dismantler = null;
                cells = List.of();
                lease = null;
                yield Descent.DONE;
            }
            case FAILED -> {
                failureReason = runner.failureReason();
                dismantler = null;
                orphan();
                cells = List.of();
                yield Descent.FAILED;
            }
        };
    }

    String failureReason() {
        return failureReason.isEmpty() ? "причина неизвестна" : failureReason;
    }

    void cancel() {
        if (climb != null) {
            climb.cancel();
            climb = null;
        }
        dismantler = null;
        orphan();
    }

    void reset() {
        climb = null;
        lease = null;
        cells = List.of();
        dismantler = null;
        failureReason = "";
    }

    private void orphan() {
        if (lease != null && !lease.cells().isEmpty()) {
            lease.orphan();
        }
        lease = null;
    }
}
