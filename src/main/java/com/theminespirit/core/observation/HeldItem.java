package com.theminespirit.core.observation;

import java.util.Objects;

public record HeldItem(String item, Integer durability) {
    public HeldItem {
        Objects.requireNonNull(item, "item не может быть null: предмет в руке называет свой id");
        if (durability != null && durability < 0) {
            throw new IllegalArgumentException("Прочность " + durability
                    + " отрицательная: у предмета в руке не бывает отрицательного остатка прочности");
        }
    }

    public HeldItem(String item) {
        this(item, null);
    }
}
