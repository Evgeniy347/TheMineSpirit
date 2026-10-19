package com.theminespirit.core.engine;

public final class UnknownScriptException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String reason;

    public UnknownScriptException(String message) {
        super(message);
        this.reason = message;
    }

    @Override
    public String getMessage() {
        return reason;
    }
}
