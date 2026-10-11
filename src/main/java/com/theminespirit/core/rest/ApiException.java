package com.theminespirit.core.rest;

import java.util.Map;

public final class ApiException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final transient ApiError error;

    public ApiException(ErrorCode code, String message) {
        this(new ApiError(code, message, Map.of()), null);
    }

    public ApiException(ErrorCode code, String message, Map<String, Object> details) {
        this(new ApiError(code, message, details), null);
    }

    public ApiException(ErrorCode code, String message, Map<String, Object> details, Throwable cause) {
        this(new ApiError(code, message, details), cause);
    }

    private ApiException(ApiError error, Throwable cause) {
        super(error.message(), cause);
        this.error = error;
    }

    public ApiError error() {
        return error;
    }
}
