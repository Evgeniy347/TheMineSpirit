package com.theminespirit.core.rest;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.ui.BoardTileGroup;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record BoardListQuery(
        int limit,
        int offset,
        @Nullable BoardStatus status,
        @Nullable BoardTileGroup group,
        @Nullable String assignee,
        @Nullable String boardCase,
        long since) {
    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 200;

    private static final String FIELD = "field";
    private static final String LIMIT = "limit";
    private static final String OFFSET = "offset";
    private static final String CURSOR = "cursor";
    private static final String STATUS = "status";
    private static final String GROUP = "group";
    private static final String ASSIGNEE = "assignee";
    private static final String CASE = "case";
    private static final String SINCE = "since";
    private static final String UPDATED_AFTER = "updated_after";
    private static final List<String> FIELDS =
            List.of(LIMIT, OFFSET, CURSOR, STATUS, GROUP, ASSIGNEE, CASE, SINCE, UPDATED_AFTER);

    public BoardListQuery {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw invalid(
                    "'limit' = " + limit + " вне границ: от 1 до " + MAX_LIMIT + " заданий",
                    Map.of(FIELD, LIMIT, "min", 1, "max", MAX_LIMIT));
        }
        if (offset < 0) {
            throw invalid(
                    "'offset' = " + offset + " отрицателен: лента начинается с нуля", Map.of(FIELD, OFFSET, "min", 0));
        }
        if (since < 0) {
            throw invalid(
                    "'since' = " + since + " отрицателен: тик постановки задания не бывает до начала отсчёта",
                    Map.of(FIELD, SINCE, "min", 0));
        }
        if (assignee != null) {
            try {
                UUID.fromString(assignee);
            } catch (IllegalArgumentException notUuid) {
                throw invalid(
                        "'assignee' = '" + assignee + "' не UUID духа: фильтр принимает id вида "
                                + "11111111-2222-3333-4444-555555555555",
                        Map.of(FIELD, ASSIGNEE));
            }
        }
    }

    public static BoardListQuery parse(Map<String, String> query) {
        Objects.requireNonNull(query, "query не может быть null: пустой запрос задают пустой картой");
        for (String name : query.keySet()) {
            if (!FIELDS.contains(name)) {
                throw invalid(
                        "Неизвестный параметр запроса доски '" + name + "': допустимы " + FIELDS,
                        Map.of(FIELD, name, "allowed", FIELDS));
            }
        }
        int limit = intValue(query, LIMIT, DEFAULT_LIMIT);
        int offset = resolveOffset(query);
        BoardStatus status = enumValue(query, STATUS, BoardStatus.class);
        BoardTileGroup group = enumValue(query, GROUP, BoardTileGroup.class);
        String assignee = text(query.get(ASSIGNEE));
        String boardCase = caseValue(query.get(CASE));
        long since = Math.max(longValue(query, SINCE), longValue(query, UPDATED_AFTER));
        return new BoardListQuery(limit, offset, status, group, assignee, boardCase, since);
    }

    private static int resolveOffset(Map<String, String> query) {
        String cursor = text(query.get(CURSOR));
        if (cursor == null) {
            return intValue(query, OFFSET, 0);
        }
        if (query.containsKey(OFFSET)) {
            throw invalid(
                    "Параметры 'cursor' и 'offset' задают одно и то же смещение: оставьте один из них",
                    Map.of("fields", List.of(CURSOR, OFFSET)));
        }
        return BoardListings.offsetOfCursor(cursor);
    }

    private static @Nullable String caseValue(@Nullable String raw) {
        String value = text(raw);
        if (value == null) {
            return null;
        }
        try {
            return BoardCase.valueOf(value.toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException unknown) {
            throw invalid(
                    "Неизвестный кейс '" + value + "': допустимы имена BoardCase, пример BUILD_FENCE",
                    Map.of(FIELD, CASE, "allowed", caseNames()));
        }
    }

    private static <E extends Enum<E>> @Nullable E enumValue(Map<String, String> query, String name, Class<E> type) {
        String raw = text(query.get(name));
        if (raw == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw invalid(
                    "Неизвестное значение '" + raw + "' параметра '" + name + "': допустимы "
                            + Arrays.toString(type.getEnumConstants()),
                    Map.of(FIELD, name, "allowed", Arrays.toString(type.getEnumConstants())));
        }
    }

    private static int intValue(Map<String, String> query, String name, int fallback) {
        String raw = text(query.get(name));
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException notNumber) {
            throw invalid("'" + name + "' = '" + raw + "' не целое число", Map.of(FIELD, name));
        }
    }

    private static long longValue(Map<String, String> query, String name) {
        String raw = text(query.get(name));
        if (raw == null) {
            return 0L;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException notNumber) {
            throw invalid("'" + name + "' = '" + raw + "' не целое число тиков", Map.of(FIELD, name));
        }
    }

    private static List<String> caseNames() {
        return Arrays.stream(BoardCase.values()).map(Enum::name).toList();
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
}
