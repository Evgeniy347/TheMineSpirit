package com.theminespirit.neoforge;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.lease.LeaseRun;
import java.util.List;
import org.jspecify.annotations.Nullable;

final class ScaffoldClimb {
    enum Outcome {
        RUNNING,
        BUILT,
        FAILED
    }

    private final PillarColumn column;
    private Outcome outcome = Outcome.RUNNING;
    private String failureReason;

    ScaffoldClimb(SpiritEntity spirit, int height) {
        String block = PillarColumn.cheapBlock(spirit);
        if (block == null) {
            throw new IllegalArgumentException("нет материала для подмостей: нужны земля или булыжник, есть 0");
        }
        this.column = new PillarColumn(spirit, block, height);
    }

    Outcome tick() {
        if (outcome != Outcome.RUNNING) {
            return outcome;
        }
        return switch (column.tick()) {
            case RUNNING -> Outcome.RUNNING;
            case BUILT -> {
                outcome = Outcome.BUILT;
                yield outcome;
            }
            case FAILED -> fail(column.failureReason());
        };
    }

    List<BlockCoord> cells() {
        return column.cells();
    }

    @Nullable
    LeaseRun lease() {
        return column.lease();
    }

    String failureReason() {
        return failureReason;
    }

    void cancel() {
        if (column.lease() != null) {
            column.lease().orphan();
        }
    }

    private Outcome fail(String reason) {
        failureReason = reason;
        outcome = Outcome.FAILED;
        if (column.lease() != null) {
            column.lease().orphan();
        }
        return outcome;
    }
}
