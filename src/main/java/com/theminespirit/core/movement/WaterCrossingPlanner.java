package com.theminespirit.core.movement;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class WaterCrossingPlanner {
    public enum Method {
        SWIM,
        BRIDGE
    }

    // Воду не шире этого дух переплывает: мост из блоков дороже времени и расходников, чем короткий заплыв.
    public static final int SWIM_MAX_WIDTH = 4;

    private WaterCrossingPlanner() {
    }

    public static Method choose(int waterWidth, int availableBlocks) {
        if (waterWidth < 1) {
            throw new IllegalArgumentException("Ширина воды " + waterWidth
                    + " меньше 1: переправы нет, пересекать нечего");
        }
        if (availableBlocks < 0) {
            throw new IllegalArgumentException("Доступных блоков " + availableBlocks
                    + " меньше нуля: блоков не бывает отрицательно");
        }
        if (waterWidth <= SWIM_MAX_WIDTH) {
            return Method.SWIM;
        }
        return availableBlocks >= waterWidth ? Method.BRIDGE : Method.SWIM;
    }

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
