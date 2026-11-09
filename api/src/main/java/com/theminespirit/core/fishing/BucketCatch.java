package com.theminespirit.core.fishing;

public final class BucketCatch {
    private BucketCatch() {}

    public static int limit(int requested, FishingData data) {
        if (data == null) {
            throw new IllegalArgumentException(
                    "Предел ловли ведром без данных рыбалки не считается: число живёт в fishing.json");
        }
        if (requested < 1) {
            throw new IllegalArgumentException(
                    "Запрошено поймать " + requested + " ведёрной рыбы: ловить нечего, заход без рыбы не ставится");
        }
        return Math.min(requested, data.bucketCount());
    }
}
