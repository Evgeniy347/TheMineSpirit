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

    public static int total(Map<String, Integer> countByBlock) {
        Objects.requireNonNull(
                countByBlock, "Наличие блоков не может быть null: без него неизвестно, хватит ли на мост");
        int total = 0;
        for (Map.Entry<String, Integer> entry : countByBlock.entrySet()) {
            Integer count = entry.getValue();
            if (count == null || count < 0) {
                throw new IllegalArgumentException(
                        "Запас блока '" + entry.getKey() + "' " + count + ": отрицательный запас не бывает");
            }
            total = Math.addExact(total, count);
        }
        return total;
    }
}
