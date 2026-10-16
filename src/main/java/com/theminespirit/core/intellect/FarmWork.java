package com.theminespirit.core.intellect;

public record FarmWork(int matureCrops, int farmland, int seeds) {
    public static final FarmWork NONE = new FarmWork(0, 0, 0);

    public FarmWork {
        if (matureCrops < 0 || farmland < 0 || seeds < 0) {
            throw new IllegalArgumentException("Работа фермы не может быть отрицательной: зрелых посевов = "
                    + matureCrops + ", пашни = " + farmland + ", семян = " + seeds);
        }
    }

    public boolean hasHarvest() {
        return matureCrops > 0;
    }
}
