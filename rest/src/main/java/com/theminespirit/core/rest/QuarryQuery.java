package com.theminespirit.core.rest;

import com.theminespirit.core.board.route.QuarryLimits;
import com.theminespirit.core.board.route.RouteResource;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public record QuarryQuery(RouteResource resource, Optional<BlockCoord> base, int min, int max) {
    public static final int MAX_RANGE = 4096;

    public QuarryQuery {
        Objects.requireNonNull(resource, "Ресурс поиска не может быть null: поиск идёт за ресурсом");
        Objects.requireNonNull(base, "Базовая точка поиска не может быть null: пусто — центр поселения");
        if (min < 0 || max < min || max > MAX_RANGE) {
            throw invalid("Диапазон поиска " + min + ".." + max + ": минимум неотрицателен, максимум не меньше "
                    + "минимума и не больше " + MAX_RANGE + " блоков — дальше тропа не окупает добычу");
        }
    }

    public static QuarryQuery parse(Map<String, String> query) {
        Objects.requireNonNull(query, "Параметры запроса не могут быть null: пустой запрос задают пустой картой");
        RouteResource resource = resource(query.get("resource"));
        int min = integer(query, "min", QuarryLimits.DEFAULT_MIN);
        int max = integer(query, "max", QuarryLimits.DEFAULT_MAX);
        Optional<BlockCoord> base = base(query);
        return new QuarryQuery(resource, base, min, max);
    }

    private static RouteResource resource(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            throw invalid("Параметр 'resource' обязателен: место ищется для земли (dirt) или песка (sand)");
        }
        try {
            return RouteResource.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw invalid("Ресурс '" + raw + "' неизвестен: допустимо dirt, sand или snow", Map.of("resource", raw));
        }
    }

    private static int integer(Map<String, String> query, String name, int fallback) {
        String raw = query.get(name);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException broken) {
            throw invalid(
                    "Параметр '" + name + "' = '" + raw + "' не целое число: укажите расстояние в блоках",
                    Map.of("field", name));
        }
    }

    private static Optional<BlockCoord> base(Map<String, String> query) {
        boolean any = query.containsKey("x") || query.containsKey("y") || query.containsKey("z");
        if (!any) {
            return Optional.empty();
        }
        if (!query.containsKey("x") || !query.containsKey("y") || !query.containsKey("z")) {
            throw invalid(
                    "Базовая точка задаётся всеми координатами x, y и z или не задаётся совсем",
                    Map.of("fields", "x,y,z"));
        }
        return Optional.of(new BlockCoord(integer(query, "x", 0), integer(query, "y", 0), integer(query, "z", 0)));
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.INVALID_REQUEST, message, Map.of("query", "quarry"));
    }

    private static ApiException invalid(String message, Map<String, Object> details) {
        return new ApiException(ErrorCode.INVALID_REQUEST, message, details);
    }
}
