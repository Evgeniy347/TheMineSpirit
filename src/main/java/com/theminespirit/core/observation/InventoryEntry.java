package com.theminespirit.core.observation;

import java.util.Objects;

public record InventoryEntry(String item, int count) {
    public InventoryEntry {
        Objects.requireNonNull(item, "item не может быть null: запись инвентаря называет предмет");
    }
}
