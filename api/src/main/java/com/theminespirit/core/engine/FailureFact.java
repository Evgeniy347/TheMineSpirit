package com.theminespirit.core.engine;

public enum FailureFact {
    NO_PATH(false),
    NO_TREES(true),
    NIGHT(true),
    NO_MATERIAL(true),
    NO_TOOL(true);

    private final boolean survivesFallback;

    FailureFact(boolean survivesFallback) {
        this.survivesFallback = survivesFallback;
    }

    public boolean survivesFallback() {
        return survivesFallback;
    }
}
