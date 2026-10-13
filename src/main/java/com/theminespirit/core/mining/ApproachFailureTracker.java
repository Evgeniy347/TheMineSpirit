package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ApproachFailureTracker {
    private final Map<BlockCoord, Integer> standFailures = new HashMap<>();
    private final Map<BlockCoord, Integer> targetFailures = new HashMap<>();
    private final Set<BlockCoord> excludedStands = new HashSet<>();

    public boolean recordFailure(BlockCoord stand, BlockCoord target) {
        Objects.requireNonNull(stand, "Стоянка не может быть null: без неё непонятно, какое место исключать");
        Objects.requireNonNull(target, "Цель не может быть null: без неё непонятно, чей счётчик попыток вести");
        int standFails = standFailures.getOrDefault(stand, 0) + 1;
        standFailures.put(stand, standFails);
        if (standFails >= MiningTuning.MAX_APPROACH_FAILURES) {
            excludedStands.add(stand);
        }
        int targetFails = targetFailures.getOrDefault(target, 0) + 1;
        if (targetFails >= MiningTuning.MAX_APPROACH_FAILURES) {
            return true;
        }
        targetFailures.put(target, targetFails);
        return false;
    }

    public Set<BlockCoord> excludedStands() {
        return Collections.unmodifiableSet(excludedStands);
    }
}
