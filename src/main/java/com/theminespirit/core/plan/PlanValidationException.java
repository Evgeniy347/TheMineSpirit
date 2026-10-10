package com.theminespirit.core.plan;

public final class PlanValidationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public PlanValidationException(String message) {
        super(message);
    }

    public PlanValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
