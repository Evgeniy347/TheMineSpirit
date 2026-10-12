package com.theminespirit.core.perception;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;

public record HazardRef(HazardType type, BlockCoord pos, double distance) {
    public HazardRef {
        Objects.requireNonNull(type, "type не может быть null: опасность обязана назвать свой вид");
        Objects.requireNonNull(pos, "pos не может быть null: опасность обязана назвать позицию");
        if (!Double.isFinite(distance) || distance < 0) {
            throw new IllegalArgumentException("Расстояние до опасности " + distance
                    + " недопустимо: ждём конечное число не меньше нуля, иначе ближайшая опасность выбирается неверно");
        }
    }
}
