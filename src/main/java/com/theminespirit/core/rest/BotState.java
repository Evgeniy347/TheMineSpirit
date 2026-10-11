package com.theminespirit.core.rest;

import java.util.Locale;

public enum BotState {
    IDLE,
    BUSY,
    DEAD;

    public String wire() {
        return name().toLowerCase(Locale.ROOT);
    }
}
