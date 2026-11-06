package com.theminespirit.core.movement;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class WaterCrossingPlanner {
    private WaterCrossingPlanner() {}

    public static Optional<String> cheapestAvailable(Map<String, Integer> countByBlock) {
        Objects.requireNonNull(countByBlock, "Наличие блоков не может быть null: без него мост не из чего строить");
        for (String id : ToolBreakCost.CHEAP_ORDER) {
            if (countByBlock.getOrDefault(id, 0) > 0) {
                return Optional.of(id);
            }
        }
        return Optional.empty();
    }
}
