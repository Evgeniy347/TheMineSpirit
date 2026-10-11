package com.theminespirit.core.tactics;

import java.util.Locale;

public enum CombatMode {
    PASSIVE,
    DEFENSIVE,
    PROTECT,
    GUARD_AREA,
    AGGRESSIVE;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
