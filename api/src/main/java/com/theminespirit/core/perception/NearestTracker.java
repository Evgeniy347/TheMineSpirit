package com.theminespirit.core.perception;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class NearestTracker {
    private final BlockCoord origin;
    private @Nullable BlockCoord best;
    private long bestDistance;

    public NearestTracker(BlockCoord origin) {
        this.origin = Objects.requireNonNull(origin, "origin не может быть null: ближайшее считается от точки духа");
    }

    public void offer(BlockCoord candidate) {
        Objects.requireNonNull(candidate, "candidate не может быть null: кандидат обязан назвать позицию");
        long distance = distanceSquared(candidate);
        if (best == null || distance < bestDistance || (distance == bestDistance && candidate.compareTo(best) < 0)) {
            best = candidate;
            bestDistance = distance;
        }
    }

    public Optional<BlockCoord> best() {
        return Optional.ofNullable(best);
    }

    private long distanceSquared(BlockCoord pos) {
        long dx = (long) pos.x() - origin.x();
        long dy = (long) pos.y() - origin.y();
        long dz = (long) pos.z() - origin.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
