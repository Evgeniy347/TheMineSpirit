package com.theminespirit.core.town;

public final class FloatingSweep {
    public static final int SLICES_PER_STEP = 1;
    public static final int STEP_EVERY_TICKS = 4;

    private FloatingSweep() {}

    public static long cycleTicks(int slices) {
        if (slices < 0) {
            throw new IllegalArgumentException("Срезов обхода " + slices + " меньше нуля: их число неотрицательно");
        }
        long steps = (slices + SLICES_PER_STEP - 1L) / SLICES_PER_STEP;
        return steps * STEP_EVERY_TICKS;
    }
}
