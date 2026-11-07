package com.theminespirit.core.board;

import com.theminespirit.core.board.graph.GraphState;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class WarehouseApproval {
    public static final String BASE = "warehouse";

    private WarehouseApproval() {}

    public static void apply(Map<String, GraphState.Built> buildings, int builtLevel, @Nullable WarehouseOrder order) {
        Objects.requireNonNull(buildings, "Постройки графа не могут быть null: склад отмечается среди них");
        for (int level = 1; level <= WarehouseRules.MAX_LEVEL; level++) {
            buildings.put(id(level), GraphState.Built.ABSENT);
        }
        if (builtLevel > 0) {
            for (int level = 1; level <= builtLevel; level++) {
                buildings.put(id(level), GraphState.Built.BUILT);
            }
        }
        int desired = order == null ? 0 : order.level();
        int next = builtLevel + 1;
        if (desired >= next && next <= WarehouseRules.MAX_LEVEL) {
            buildings.put(id(next), GraphState.Built.PLANNED);
        }
    }

    public static String id(int level) {
        if (level < 1 || level > WarehouseRules.MAX_LEVEL) {
            throw new IllegalArgumentException("Уровень склада " + level + " вне 1.." + WarehouseRules.MAX_LEVEL
                    + ": узел графа для такого уровня не назван");
        }
        return level == 1 ? BASE : BASE + "_" + level;
    }
}
