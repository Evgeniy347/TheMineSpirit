package com.theminespirit.core.planner;

public final class BatchPlanException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final int index;

    public BatchPlanException(int index, Throwable cause) {
        super(cause.getMessage(), cause);
        this.index = index;
    }

    public int index() {
        return index;
    }
}
