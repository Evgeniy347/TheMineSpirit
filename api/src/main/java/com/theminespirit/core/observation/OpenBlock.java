package com.theminespirit.core.observation;

import java.util.Objects;

public record OpenBlock(String item, BlockCoord pos, double distance) {
    public OpenBlock {
        Objects.requireNonNull(item, "item не может быть null: запись называет блок");
        Objects.requireNonNull(pos, "pos не может быть null: открытая позиция без координат бесполезна");
        if (!Double.isFinite(distance) || distance < 0) {
            throw new IllegalArgumentException(
                    "distance = " + distance + ": расстояние не может быть отрицательным или NaN");
        }
    }
}
