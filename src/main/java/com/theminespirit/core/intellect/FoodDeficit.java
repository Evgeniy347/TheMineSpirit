package com.theminespirit.core.intellect;

public record FoodDeficit(double need, double stock) {
    public FoodDeficit {
        if (Double.isNaN(need) || Double.isInfinite(need) || need < 0) {
            throw new IllegalArgumentException(
                    "Норма еды = " + need + ": нужда в сытности обязана быть конечной и >= 0");
        }
        if (Double.isNaN(stock) || Double.isInfinite(stock) || stock < 0) {
            throw new IllegalArgumentException(
                    "Запас еды = " + stock + ": сытность запаса обязана быть конечной и >= 0");
        }
    }

    public double deficit() {
        return Math.max(0, need - stock);
    }

    public double share() {
        return need == 0 ? 0 : Math.min(1, deficit() / need);
    }
}
