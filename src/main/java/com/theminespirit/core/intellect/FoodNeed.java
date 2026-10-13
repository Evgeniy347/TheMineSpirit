package com.theminespirit.core.intellect;

public record FoodNeed(long items, double nutrition) {
    public FoodNeed {
        if (items < 0) {
            throw new IllegalArgumentException(
                    "Нужно предметов = " + items + ": нужда в еде не может быть отрицательной");
        }
        if (Double.isNaN(nutrition) || Double.isInfinite(nutrition) || nutrition < 0) {
            throw new IllegalArgumentException("Нужно сытности = " + nutrition + ": нужда обязана быть конечной и >= 0");
        }
    }
}
