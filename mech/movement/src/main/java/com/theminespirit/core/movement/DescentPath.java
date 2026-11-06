package com.theminespirit.core.movement;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class DescentPath {
    public static final int MAX_DESCENT = 12;

    private DescentPath() {}

    public static String refusal(List<BlockCoord> cells, boolean endsInWater) {
        Objects.requireNonNull(cells, "Клетки пути не могут быть null: без них не видно спуска");
        if (cells.isEmpty()) {
            return "путь пуст";
        }
        int startY = cells.get(0).y();
        int lowest = startY;
        for (BlockCoord cell : cells) {
            lowest = Math.min(lowest, cell.y());
        }
        int descent = startY - lowest;
        if (descent > MAX_DESCENT) {
            return "путь вниз на " + descent + " блоков: предел " + MAX_DESCENT;
        }
        if (endsInWater) {
            return "путь упирается в воду: выхода к поверхности нет";
        }
        return "";
    }

    public static @Nullable String refusalOrNull(List<BlockCoord> cells, boolean endsInWater) {
        String refusal = refusal(cells, endsInWater);
        return refusal.isEmpty() ? null : refusal;
    }
}
