package com.theminespirit.core.tactics.adapter;

public final class TacticsFormatException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public TacticsFormatException(String message) {
        super(message);
    }

    public TacticsFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
