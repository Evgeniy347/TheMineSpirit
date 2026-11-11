package com.theminespirit.neoforge;

import com.theminespirit.core.engine.FailureFact;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.lease.LeaseRun;
import java.util.List;
import net.minecraft.core.BlockPos;
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
        this(spirit, height, spirit.blockPosition());
    }

    ScaffoldClimb(SpiritEntity spirit, int height, @Nullable LeaseRun lease) {
        this(spirit, height, spirit.blockPosition(), lease);
    }

    ScaffoldClimb(SpiritEntity spirit, int height, BlockPos base) {
        this(spirit, height, base, null);
    }

    ScaffoldClimb(SpiritEntity spirit, int height, BlockPos base, @Nullable LeaseRun lease) {
        String block = PillarColumn.cheapBlock(spirit);
        if (block == null) {
            throw new IllegalArgumentException("нет материала для подмостей: нужны земля или булыжник, есть 0");
        }
        this.column = new PillarColumn(spirit, block, height, base, lease);
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

    @Nullable
    FailureFact failureFact() {
        return column.failureFact();
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
