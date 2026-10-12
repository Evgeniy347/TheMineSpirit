package com.theminespirit.core.perception;

public final class MemoryFormatException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public MemoryFormatException(String message) {
        super(message);
    }

    public MemoryFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
