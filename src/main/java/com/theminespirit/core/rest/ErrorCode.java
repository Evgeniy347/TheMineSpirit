package com.theminespirit.core.rest;

public enum ErrorCode {
    INVALID_REQUEST("invalid_request", 400),
    FORBIDDEN("forbidden", 403),
    NOT_FOUND("not_found", 404),
    CONFLICT("conflict", 409),
    INVALID_PLAN("invalid_plan", 422),
    RATE_LIMITED("rate_limited", 429),
    INTERNAL("internal", 500);

    private final String wire;
    private final int status;

    ErrorCode(String wire, int status) {
        this.wire = wire;
        this.status = status;
    }

    public String wire() {
        return wire;
    }

    public int status() {
        return status;
    }
}
