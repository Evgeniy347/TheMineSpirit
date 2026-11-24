package com.theminespirit.core.rest;

import org.jspecify.annotations.Nullable;

public record SettlementLevelPreview(
        int level,
        int y0,
        int cut,
        int fill,
        int surplusPercent,
        int stockBlocks,
        boolean stockCovered,
        int waterBlocks,
        @Nullable Integer waterMedian,
        @Nullable Integer waterFloor,
        int columns,
        int min,
        int max,
        int x,
        int z) {
    public SettlementLevelPreview {
        if (y0 != level - 1) {
            throw new IllegalArgumentException(
                    "Уровень ног " + level + " и верх грунта " + y0 + ": y0 обязан быть на 1 ниже");
        }
        if (cut < 0 || fill < 0 || stockBlocks < 0 || waterBlocks < 0 || columns < 1) {
            throw new IllegalArgumentException(
                    "Объёмы уровня отрицательные или колонок нет: срезка " + cut + ", подсыпка " + fill + ", запас "
                            + stockBlocks + ", вода " + waterBlocks + ", колонок " + columns);
        }
        if (min > level || max < level) {
            throw new IllegalArgumentException("Высота " + level + " вне рельефа [" + min + ", " + max
                    + "]: медиана и границы участка не сходятся");
        }
    }
}
