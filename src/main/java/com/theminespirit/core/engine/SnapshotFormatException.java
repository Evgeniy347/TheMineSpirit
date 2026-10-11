package com.theminespirit.core.engine;

public final class SnapshotFormatException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public SnapshotFormatException(String message) {
        super(message);
    }

    public SnapshotFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
