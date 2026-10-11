package com.theminespirit.core.tactics;

import java.util.Locale;

public enum TacticalState {
    CALM,
    ENGAGED,
    RETREATING,
    RECOVERING;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
