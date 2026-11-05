package com.theminespirit.core.planner;

import org.jspecify.annotations.Nullable;

public final class UnplannableGoalException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final @Nullable String item;

    public UnplannableGoalException(String message) {
        this(null, message);
    }

    public UnplannableGoalException(@Nullable String item, String message) {
        super(message);
        this.item = item;
    }

    public @Nullable String item() {
        return item;
    }
}
