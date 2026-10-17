package com.theminespirit.core.inventory;

import java.util.Objects;

public final class WarehouseSpace {
    public static final int MIN_FREE_PERCENT = 20;

    private WarehouseSpace() {
    }

    public static boolean canTakeNewSlot(ContainerSnapshot snapshot, int minFreeSlotsPercent) {
        Objects.requireNonNull(snapshot, "Снимок контейнера не может быть null: по нему считают свободные слоты");
        if (minFreeSlotsPercent < 0 || minFreeSlotsPercent > 100) {
            throw new IllegalArgumentException("Минимум свободного места " + minFreeSlotsPercent
                    + "% вне диапазона 0..100: доля больше 100% недостижима");
        }
        int free = snapshot.freeSlots();
        if (free <= 0) {
            return false;
        }
        int slots = snapshot.slotCount();
        return (free - 1) * 100 >= slots * minFreeSlotsPercent;
    }
}
