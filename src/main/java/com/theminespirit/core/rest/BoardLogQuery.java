package com.theminespirit.core.rest;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public record BoardLogQuery(int limit) {
    public static final int DEFAULT_LIMIT = 5;
    public static final int MAX_LIMIT = 64;

    private static final String LIMIT = "limit";
    private static final List<String> FIELDS = List.of(LIMIT);

    public BoardLogQuery {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw invalid(
                    "'limit' = " + limit + " вне границ: от 1 до " + MAX_LIMIT + " записей",
                    Map.of("field", LIMIT, "min", 1, "max", MAX_LIMIT));
        }
    }

    public static BoardLogQuery parse(Map<String, String> query) {
        for (String name : query.keySet()) {
            if (!LIMIT.equals(name)) {
                throw invalid(
                        "Неизвестный параметр запроса '" + name + "' в журнале доски: допустим только 'limit'",
                        Map.of("field", name, "allowed", FIELDS));
            }
        }
        String raw = query.get(LIMIT);
        if (raw == null || raw.isEmpty()) {
            return new BoardLogQuery(DEFAULT_LIMIT);
        }
        try {
            return new BoardLogQuery(Integer.parseInt(raw.trim()));
        } catch (NumberFormatException notNumber) {
            throw invalid("'limit' = '" + raw + "' не целое число: пример limit=5", Map.of("field", LIMIT));
        }
    }

    private static ApiException invalid(String message, Map<String, Object> details) {
        return new ApiException(ErrorCode.INVALID_REQUEST, message, details);
    }

    public String wire() {
        return String.format(Locale.ROOT, "%d", limit);
    }
}
