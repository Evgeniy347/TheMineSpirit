package com.theminespirit.core.rest;

import java.util.Map;

public final class ApiException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final transient ApiError error;
    private final int status;

    public ApiException(ErrorCode code, String message) {
        this(new ApiError(code, message, Map.of()), code.status(), null);
    }

    public ApiException(ErrorCode code, String message, Map<String, Object> details) {
        this(new ApiError(code, message, details), code.status(), null);
    }

    public ApiException(ErrorCode code, String message, Map<String, Object> details, Throwable cause) {
        this(new ApiError(code, message, details), code.status(), cause);
    }

    /** Тот же код ошибки с другим HTTP-статусом: контракт различает 400 и 422 при одном wire-коде. */
    public ApiException(ErrorCode code, int status, String message, Map<String, Object> details) {
        this(new ApiError(code, message, details), status, null);
    }

    private ApiException(ApiError error, int status, Throwable cause) {
        super(error.message(), cause);
        this.error = error;
        this.status = status;
    }

    public int status() {
        return status;
    }

    public ApiError error() {
        return error;
    }
}
