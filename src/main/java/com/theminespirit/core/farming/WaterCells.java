package com.theminespirit.core.farming;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class WaterCells {
    public static final int MAX_CELLS = 20_000;
    public static final String REFUSAL = "воды слишком много: уменьши radius";

    private final Set<BlockCoord> cells = new HashSet<>();
    private boolean exceeded;

    public void add(BlockCoord cell) {
        Objects.requireNonNull(cell, "Клетка воды не может быть null: множество воды хранит только координаты");
        if (exceeded) {
            return;
        }
        cells.add(cell);
        if (cells.size() > MAX_CELLS) {
            exceeded = true;
            cells.clear();
        }
    }

    public boolean exceeded() {
        return exceeded;
    }

    public Set<BlockCoord> cells() {
        if (exceeded) {
            throw new IllegalStateException("Воды больше " + MAX_CELLS
                    + " клеток: неполное множество дало бы ложный отказ «сухо», проверь exceeded() до чтения");
        }
        return Set.copyOf(cells);
    }
}
