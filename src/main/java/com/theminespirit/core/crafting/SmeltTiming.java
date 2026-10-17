package com.theminespirit.core.crafting;

public final class SmeltTiming {
    public static final int RESERVE_TICKS = 400;

    private SmeltTiming() {
    }

    public static int expectedTicks(int count, int cookTicks) {
        if (count <= 0) {
            throw new IllegalArgumentException(
                    "Количество плавки должно быть больше 0, а получено " + count + ": ждать нечего");
        }
        if (cookTicks <= 0) {
            throw new IllegalArgumentException("Время плавки должно быть больше 0, а получено " + cookTicks
                    + ": по нему считается ожидание печи");
        }
        return Math.toIntExact((long) count * cookTicks);
    }

    public static int timeoutTicks(int count, int cookTicks) {
        return Math.addExact(expectedTicks(count, cookTicks), RESERVE_TICKS);
    }

    public static int campfireExpectedTicks(int count, int cookTicks) {
        int batches = (count + Stations.CAMPFIRE_SLOTS - 1) / Stations.CAMPFIRE_SLOTS;
        return expectedTicks(batches, cookTicks);
    }

    public static int campfireTimeoutTicks(int count, int cookTicks) {
        return Math.addExact(campfireExpectedTicks(count, cookTicks), RESERVE_TICKS);
    }
}
