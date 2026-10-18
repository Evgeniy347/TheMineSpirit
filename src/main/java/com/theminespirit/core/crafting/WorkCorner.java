package com.theminespirit.core.crafting;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;
import java.util.Optional;

public final class WorkCorner {
    private WorkCorner() {
    }

    public static BlockCoord anchor(BlockCoord spirit, Optional<BlockCoord> station, int maxRange) {
        Objects.requireNonNull(spirit, "Точка духа не может быть null: без неё некуда ставить станцию");
        Objects.requireNonNull(station, "Станция не может быть null: пустую задают Optional.empty()");
        if (maxRange < 0) {
            throw new IllegalArgumentException("Дальность рабочего угла " + maxRange
                    + " отрицательна: станция дальше нуля от духа не считается своим углом");
        }
        if (station.isEmpty()) {
            return spirit;
        }
        BlockCoord point = station.get();
        long dx = (long) point.x() - spirit.x();
        long dy = (long) point.y() - spirit.y();
        long dz = (long) point.z() - spirit.z();
        if (dx * dx + dy * dy + dz * dz > (long) maxRange * maxRange) {
            return spirit;
        }
        return point;
    }
}
