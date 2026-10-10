package com.theminespirit.core.planner;

public final class UnplannableGoalException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public UnplannableGoalException(String message) {
        super(message);
    }

    public UnplannableGoalException(String message, Throwable cause) {
        super(message, cause);
    }
}