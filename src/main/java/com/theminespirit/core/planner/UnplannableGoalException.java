package com.theminespirit.core.planner;

public final class UnplannableGoalException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String item;

    public UnplannableGoalException(String message) {
        this(null, message);
    }

    public UnplannableGoalException(String item, String message) {
        super(message);
        this.item = item;
    }

    public String item() {
        return item;
    }
}
