package com.theminespirit.core.husbandry;

import com.theminespirit.core.husbandry.RingGround.Cell;
import java.util.Objects;

public final class CaptureFollow {
    private CaptureFollow() {
    }

    public static final int LEAD = 2;

    public static Cell leadingCell(Cell current, Cell previous) {
        Objects.requireNonNull(current, "Текущая клетка животного не может быть null: без неё неизвестно, куда оно идёт");
        if (previous == null || previous.equals(current)) {
            return current;
        }
        int dx = Integer.signum(current.x() - previous.x());
        int dz = Integer.signum(current.z() - previous.z());
        return new Cell(current.x() + dx * LEAD, current.z() + dz * LEAD);
    }

    public static boolean ringCovers(RingPlan.Ring ring, Cell animal) {
        Objects.requireNonNull(ring, "Кольцо не может быть null: без него неизвестно, что покрыто");
        Objects.requireNonNull(animal, "Клетка животного не может быть null: без неё неизвестно, что проверять");
        return ring.interior().contains(animal);
    }
}