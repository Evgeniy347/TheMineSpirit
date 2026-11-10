package com.theminespirit.core.rest;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.theminespirit.core.board.route.QuarryArea;
import com.theminespirit.core.board.route.QuarrySite;
import com.theminespirit.core.board.route.RouteResource;
import com.theminespirit.core.observation.BlockCoord;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public record QuarryInstall(RouteResource resource, BlockCoord point, int radius, String biome, String by) {
    private static final String FIELD = "field";
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    public QuarryInstall {
        Objects.requireNonNull(resource, "Ресурс места не может быть null: место ставится под ресурс");
        Objects.requireNonNull(point, "Точка места не может быть null: без координат место не поставить");
        if (radius < 1 || radius > QuarryArea.MAX_RADIUS) {
            throw invalid("Радиус " + radius + ": допустимо 1.." + QuarryArea.MAX_RADIUS, Map.of(FIELD, "radius"));
        }
        if (biome == null) {
            throw invalid("Биом не может быть null: пусто — пустая строка", Map.of(FIELD, "biome"));
        }
        if (by == null || by.isBlank()) {
            throw invalid("Поле 'by' обязательно: strategist или player", Map.of(FIELD, "by"));
        }
        if ("graph".equalsIgnoreCase(by.trim())) {
            throw invalid(
                    "Место добычи выбирает стратег или игрок, а не граф: поле 'by' = 'graph' запрещено",
                    Map.of(FIELD, "by"));
        }
    }

    public QuarrySite site() {
        return new QuarrySite(resource, point, radius, biome, 0);
    }

    public static QuarryInstall parse(byte[] body) {
        JsonNode root;
        try {
            root = MAPPER.readTree(body);
        } catch (IOException broken) {
            throw invalid("Тело установки места добычи не JSON: " + broken.getMessage(), Map.of());
        }
        if (root == null || !root.isObject()) {
            throw invalid(
                    "Тело POST/PUT quarry site должно быть объектом вида "
                            + "{\"resource\":\"dirt\",\"x\":0,\"y\":64,\"z\":0,\"radius\":4,\"biome\":\"minecraft:plains\","
                            + "\"by\":\"strategist\"}",
                    Map.of());
        }
        RouteResource resource = resource(root.path("resource").asText(""));
        int x = integer(root, "x");
        int y = integer(root, "y");
        int z = integer(root, "z");
        int radius = root.has("radius") ? integer(root, "radius") : QuarryArea.DEFAULT_RADIUS;
        String biome = root.path("biome").asText("");
        String by = root.path("by").asText("strategist");
        return new QuarryInstall(resource, new BlockCoord(x, y, z), radius, biome, by);
    }

    private static RouteResource resource(String raw) {
        try {
            return RouteResource.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw invalid("Ресурс '" + raw + "' неизвестен: допустимо dirt, sand или snow", Map.of(FIELD, "resource"));
        }
    }

    private static int integer(JsonNode root, String name) {
        JsonNode value = root.get(name);
        if (value == null || !value.isIntegralNumber()) {
            throw invalid(
                    "Поле '" + name + "' обязательно и целое: точка места добычи задаётся в блоках",
                    Map.of(FIELD, name));
        }
        return value.asInt();
    }

    private static ApiException invalid(String message, Map<String, Object> details) {
        return new ApiException(
                ErrorCode.INVALID_REQUEST,
                message,
                details.isEmpty() ? Map.of("body", List.of("resource", "x", "y", "z")) : details);
    }
}
