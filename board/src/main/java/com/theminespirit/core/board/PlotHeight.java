package com.theminespirit.core.board;

import com.theminespirit.core.town.ColumnMask;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.IntBinaryOperator;

public record PlotHeight(int level, int cut, int fill, int surplusPercent) {

    public String describe() {
        return "[plot] высота " + level + ": срезка " + cut + ", подсыпка " + fill + ", запас " + surplusPercent + "%";
    }

    public static PlotHeight choose(ColumnMask area, IntBinaryOperator feet, LevelingRules rules, int lowest) {
        return choose(area, feet, rules, lowest, 0);
    }

    public static PlotHeight choose(
            ColumnMask area, IntBinaryOperator feet, LevelingRules rules, int lowest, int fillStock) {
        if (fillStock < 0) {
            throw new IllegalArgumentException(
                    "Запас земли " + fillStock + " отрицательный: бюджет подсыпки не может быть должником");
        }
        int[] heights = heights(area, feet);
        int[] sorted = heights.clone();
        Arrays.sort(sorted);
        int bottom = Math.max(sorted[0], lowest);
        for (int level = sorted[sorted.length / 2]; level > bottom; level--) {
            PlotHeight candidate = measure(heights, level, rules);
            if (rules.enough(candidate.cut(), candidate.fill(), fillStock)) {
                return candidate;
            }
        }
        return measure(heights, bottom, rules);
    }

    private static PlotHeight measure(int[] heights, int level, LevelingRules rules) {
        int cut = 0;
        int fill = 0;
        for (int height : heights) {
            cut += Math.max(0, height - level);
            fill += Math.max(0, level - height);
        }
        return new PlotHeight(level, cut, fill, rules.surplusPercent());
    }

    private static int[] heights(ColumnMask area, IntBinaryOperator feet) {
        Objects.requireNonNull(area, "Площадки не могут быть null: высоты читаются на них");
        Objects.requireNonNull(feet, "Высоты колонок не могут быть null: без них высоту участка не выбрать");
        if (area.isEmpty()) {
            throw new IllegalArgumentException("Площадок под выравнивание нет: высоту этапа не из чего выбрать");
        }
        return area.cells().stream()
                .mapToInt(cell -> feet.applyAsInt(cell.x(), cell.z()))
                .toArray();
    }
}
