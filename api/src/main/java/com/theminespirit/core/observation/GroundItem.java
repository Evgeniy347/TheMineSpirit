package com.theminespirit.core.observation;

import java.util.Objects;

public record GroundItem(String item, int count) {
    public GroundItem {
        Objects.requireNonNull(item, "item не может быть null: выпавший предмет называет свой id");
        if (count <= 0) {
            throw new IllegalArgumentException("Выпавший предмет '" + item + "' с количеством " + count
                    + ": пустая запись не описывает ни одного предмета на земле");
        }
    }
}
