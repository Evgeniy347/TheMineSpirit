package com.theminespirit.core.rest;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public record BoardLogQuery(int limit, long sinceTick) {
    public static final int DEFAULT_LIMIT = 5;
    public static final int MAX_LIMIT = 64;

    private static final String LIMIT = "limit";
    private static final String SINCE_TICK = "sinceTick";
    private static final String FIELD = "field";
    private static final List<String> FIELDS = List.of(LIMIT, SINCE_TICK);

    public BoardLogQuery {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw invalid(
                    "'limit' = " + limit + " вне границ: от 1 до " + MAX_LIMIT + " записей",
                    Map.of(FIELD, LIMIT, "min", 1, "max", MAX_LIMIT));
        }
        if (sinceTick < 0) {
            throw invalid(
                    "'sinceTick' = " + sinceTick + " отрицателен: тик записи не бывает до начала отсчёта",
                    Map.of(FIELD, SINCE_TICK, "min", 0));
        }
    }

    public static BoardLogQuery parse(Map<String, String> query) {
        for (String name : query.keySet()) {
            if (!FIELDS.contains(name)) {
                throw invalid(
                        "Неизвестный параметр запроса '" + name + "' в журнале доски: допустимы " + FIELDS,
                        Map.of(FIELD, name, "allowed", FIELDS));
            }
        }
        return new BoardLogQuery(intValue(query, LIMIT, DEFAULT_LIMIT), longValue(query, SINCE_TICK, 0));
    }

    private static int intValue(Map<String, String> query, String name, int fallback) {
        String raw = text(query.get(name));
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException notNumber) {
            throw invalid("'" + name + "' = '" + raw + "' не целое число: пример limit=5", Map.of(FIELD, name));
        }
    }

    private static long longValue(Map<String, String> query, String name, long fallback) {
        String raw = text(query.get(name));
        if (raw == null) {
            return fallback;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException notNumber) {
            throw invalid(
                    "'" + name + "' = '" + raw + "' не целое число тиков: пример sinceTick=29450000",
                    Map.of(FIELD, name));
        }
    }

    private static @Nullable String text(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static ApiException invalid(String message, Map<String, Object> details) {
        return new ApiException(ErrorCode.INVALID_REQUEST, message, details);
    }

    public String wire() {
        return String.format(Locale.ROOT, "%d", limit);
    }
}
