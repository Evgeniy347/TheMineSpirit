package com.theminespirit.core.pickup;

public record PickupTuning(int refreshTicks, double pickupReach, int totalTimeoutTicks, int maxDelayWaitTicks) {
    public static final int REFRESH_TICKS = 10;
    public static final double PICKUP_REACH = 1.5;
    public static final int TOTAL_TIMEOUT_TICKS = 2400;
    public static final int MAX_DELAY_WAIT_TICKS = 40;
    public static final int MAX_APPROACH_ATTEMPTS = 2;
    public static final PickupTuning DEFAULT =
            new PickupTuning(REFRESH_TICKS, PICKUP_REACH, TOTAL_TIMEOUT_TICKS, MAX_DELAY_WAIT_TICKS);
    public static final int SELF_DROP_DELAY_TICKS = 400;
    public static final int OWN_DROP_SKIP_TICKS = 6000;

    public PickupTuning {
        if (refreshTicks < 1) {
            throw new IllegalArgumentException("Период опроса предметов " + refreshTicks
                    + " тиков меньше единицы: опрос каждый тик нагружает сервер зря");
        }
        if (!(pickupReach > 0)) {
            throw new IllegalArgumentException(
                    "Дальность подбора " + pickupReach + " не положительна: дух никогда не окажется достаточно близко");
        }
        if (totalTimeoutTicks < 1) {
            throw new IllegalArgumentException("Общий тайм-аут подбора " + totalTimeoutTicks
                    + " тиков меньше единицы: задача провалилась бы до начала");
        }
        if (maxDelayWaitTicks < 0) {
            throw new IllegalArgumentException("Ожидание задержки подбора " + maxDelayWaitTicks
                    + " тиков отрицательно: дух пропускал бы кучку, не дав ей дозреть");
        }
    }
}
