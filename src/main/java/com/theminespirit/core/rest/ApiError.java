package com.theminespirit.core.rest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record ApiError(
        ErrorCode code, String message, @Nullable Map<String, Object> details) {
    public ApiError {
        Objects.requireNonNull(code, "code не может быть null: клиент выбирает поведение по коду ошибки");
        Objects.requireNonNull(message, "message не может быть null: ошибка без текста не объясняет, что проверить");
        details = details == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    public static ApiError of(ErrorCode code, String message) {
        return new ApiError(code, message, Map.of());
    }
}
