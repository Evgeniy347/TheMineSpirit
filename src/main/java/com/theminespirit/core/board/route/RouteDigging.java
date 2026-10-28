package com.theminespirit.core.board.route;

import com.theminespirit.core.board.PlotLeveling;
import com.theminespirit.core.town.ColumnMask;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntBinaryOperator;

public final class RouteDigging {
    public static final int MAX_WATER = 10;
    public static final int WATER_RADIUS = 2;

    @FunctionalInterface
    public interface WaterAround {
        int count(int x, int y, int z);
    }

    private RouteDigging() {}

    public static PlotLeveling plan(ColumnMask area, IntBinaryOperator tops, WaterAround water, int waterLevel) {
        Objects.requireNonNull(area, "Площадка добычи не может быть null: копают по её колонкам");
        Objects.requireNonNull(tops, "Верхний слой не может быть null: без него нечего снимать");
        Objects.requireNonNull(water, "Счётчик воды не может быть null: без него слой не проверить на затопление");
        int lowest = Integer.MAX_VALUE;
        for (ColumnMask.Cell cell : area.cells()) {
            int top = tops.applyAsInt(cell.x(), cell.z());
            if (top > waterLevel) {
                lowest = Math.min(lowest, top);
            }
        }
        if (lowest == Integer.MAX_VALUE) {
            return new PlotLeveling(List.of(), List.of(), PlotLeveling.DEFAULT_BLOCK);
        }
        int target = Math.max(lowest, waterLevel);
        List<PlotLeveling.Box> cut = new ArrayList<>();
        for (ColumnMask.Cell cell : area.cells()) {
            int top = tops.applyAsInt(cell.x(), cell.z());
            if (top <= target) {
                continue;
            }
            boolean safe = true;
            for (int y = target + 1; y <= top; y++) {
                if (water.count(cell.x(), y, cell.z()) > MAX_WATER) {
                    safe = false;
                    break;
                }
            }
            if (safe) {
                cut.add(new PlotLeveling.Box(cell.x(), target + 1, cell.z(), cell.x(), top, cell.z()));
            }
        }
        return new PlotLeveling(cut, List.of(), PlotLeveling.DEFAULT_BLOCK);
    }

    public static List<PlotLeveling.Box> layers(
            ColumnMask area, IntBinaryOperator tops, WaterAround water, int waterLevel) {
        return plan(area, tops, water, waterLevel).cutTopDown();
    }

    public static int waterLevel(int[] waterTops) {
        int level = Integer.MIN_VALUE;
        for (int top : waterTops) {
            level = Math.max(level, top);
        }
        return level;
    }
}
