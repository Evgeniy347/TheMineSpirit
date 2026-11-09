package com.theminespirit.core.ui;

import java.util.Objects;

public record MapQuarry(String resource, boolean found, int percent, boolean low, boolean empty) {
    public static final int PERCENT = 100;

    public MapQuarry {
        Objects.requireNonNull(resource, "Ресурс места на карте не может быть null: у значка один ресурс");
        if (percent < 0 || percent > PERCENT) {
            throw new IllegalArgumentException("Остаток " + percent + "%: на карте остаток в 0..100");
        }
        if (!found && (percent != 0 || low || empty)) {
            throw new IllegalArgumentException("Место " + resource + " не найдено, но несёт остаток " + percent
                    + "%: перечёркнутый значок не показывает проценты");
        }
    }

    public static MapQuarry unset(String resource) {
        return new MapQuarry(resource, false, 0, false, false);
    }

    public static MapQuarry of(String resource, String state, int percent) {
        boolean found = !"unset".equals(state);
        return new MapQuarry(
                resource, found, found ? percent : 0, found && "low".equals(state), found && "empty".equals(state));
    }
}
