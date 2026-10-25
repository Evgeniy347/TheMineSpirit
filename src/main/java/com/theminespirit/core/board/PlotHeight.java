package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntBinaryOperator;

public record PlotHeight(int level, int cut, int fill, int surplusPercent) {

    public String describe() {
        return "[plot] высота " + level + ": срезка " + cut + ", подсыпка " + fill + ", запас " + surplusPercent + "%";
    }

    public static PlotHeight choose(BlockCoord center, int half, IntBinaryOperator feet, LevelingRules rules) {
        int[] heights = heights(center, half, feet);
        int[] sorted = heights.clone();
        Arrays.sort(sorted);
        for (int level = sorted[sorted.length / 2]; level > sorted[0]; level--) {
            PlotHeight candidate = measure(heights, level, rules);
            if (rules.enough(candidate.cut(), candidate.fill())) {
                return candidate;
            }
        }
        return measure(heights, sorted[0], rules);
    }

    public static PlotHeight at(int level, BlockCoord center, int half, IntBinaryOperator feet, LevelingRules rules) {
        return measure(heights(center, half, feet), level, rules);
    }

    public static Optional<PlotHeight> recheck(
            SettlementPlot stored, int half, IntBinaryOperator feet, LevelingRules rules) {
        Objects.requireNonNull(stored, "План участка не может быть null: перепроверяется сохранённая высота");
        BlockCoord anchor = stored.anchor();
        if (PlotLeveling.isLevel(anchor, half, feet)) {
            return Optional.empty();
        }
        PlotHeight current = at(anchor.y(), anchor, half, feet, rules);
        if (rules.enough(current.cut(), current.fill())) {
            return Optional.empty();
        }
        return Optional.of(choose(anchor, half, feet, rules));
    }

    private static PlotHeight measure(int[] heights, int level, LevelingRules rules) {
        int cut = 0;
        int fill = 0;
        for (int height : heights) {
            cut += Math.max(0, Math.min(height - level, PlotLeveling.MAX_SHIFT));
            fill += Math.max(0, Math.min(level - height, PlotLeveling.MAX_SHIFT));
        }
        return new PlotHeight(level, cut, fill, rules.surplusPercent());
    }

    private static int[] heights(BlockCoord center, int half, IntBinaryOperator feet) {
        Objects.requireNonNull(center, "Центр участка не может быть null: высоты читаются вокруг него");
        Objects.requireNonNull(feet, "Высоты колонок не могут быть null: без них высоту участка не выбрать");
        int side = 2 * half + 1;
        int[] heights = new int[side * side];
        int index = 0;
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                heights[index++] = feet.applyAsInt(center.x() + dx, center.z() + dz);
            }
        }
        return heights;
    }
}
