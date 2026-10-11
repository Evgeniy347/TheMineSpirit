package com.theminespirit.core.observation;

import java.util.Objects;

public record ItemCount(String item, int count) {
    public ItemCount {
        Objects.requireNonNull(item, "item не может быть null: запись называет предмет");
    }
}
