package com.theminespirit.core.inventory;

import java.util.Objects;

public final class WarehouseSpace {
    public static final int MIN_FREE_PERCENT = 20;

    private WarehouseSpace() {}

    public static boolean needsChest(int freeSlots, int slotCount, int minFreePercent) {
        if (freeSlots < 0 || slotCount < freeSlots || minFreePercent < 0 || minFreePercent > 100) {
            throw new IllegalArgumentException("Свободно " + freeSlots + " из " + slotCount + " слотов при пороге "
                    + minFreePercent + "%: свободных не больше всех, порог 0..100");
        }
        return slotCount > 0 && (long) freeSlots * 100 < (long) slotCount * minFreePercent;
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
