package com.theminespirit.core.rest;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.theminespirit.core.board.WarehouseOrder;
import com.theminespirit.core.observation.BlockCoord;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record WarehouseDecision(UUID requestId, int level, BlockCoord site, String by, String reason) {
    private static final String FIELD = "field";
    private static final String REQUEST_ID = "request_id";
    private static final String LEVEL = "level";
    private static final String SITE = "site";
    private static final String BY = "by";
    private static final String REASON = "reason";
    private static final List<String> FIELDS = List.of(REQUEST_ID, LEVEL, SITE, BY, REASON);
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    public WarehouseDecision {
        if (requestId == null) {
            throw invalid(
                    "Поле 'request_id' обязательно: повтор решения о складе возвращает прежний ответ",
                    Map.of(FIELD, REQUEST_ID));
        }
        if (level < 1) {
            throw invalid(
                    "Поле 'level' = " + level + " меньше 1: у склада есть только первый уровень", Map.of(FIELD, LEVEL));
        }
        if (site == null) {
            throw invalid("Поле 'site' обязательно: стратег называет место склада", Map.of(FIELD, SITE));
        }
        if (by == null || by.isBlank()) {
            throw invalid("Поле 'by' пусто: по нему видно, кто решил строить склад", Map.of(FIELD, BY));
        }
        if (reason == null || reason.isBlank()) {
            throw invalid("Поле 'reason' пусто: по причине видно, зачем строить склад", Map.of(FIELD, REASON));
        }
    }

    public static WarehouseDecision parse(byte[] body) {
        JsonNode root = read(body);
        if (root == null || root.isNull() || !root.isObject()) {
            throw invalid(
                    "Тело POST settlement warehouse должно быть JSON-объектом вида {\"request_id\":\"<uuid>\","
                            + "\"level\":2,\"site\":\"10,64,-3\"}, получено: "
                            + (root == null ? "пустое тело" : root.getNodeType()),
                    Map.of("allowed", FIELDS));
        }
        rejectUnknownFields(root);
        return new WarehouseDecision(
                requestId(root.get(REQUEST_ID)),
                level(root.get(LEVEL)),
                site(root.get(SITE)),
                text(root.get(BY), BY, WarehouseOrder.DEFAULT_BY),
                text(root.get(REASON), REASON, WarehouseOrder.DEFAULT_REASON));
    }

    private static UUID requestId(@Nullable JsonNode node) {
        if (node == null || !node.isTextual()) {
            throw invalid(
                    "Поле 'request_id' обязательно и должно быть строкой-UUID: повтор с тем же request_id "
                            + "возвращает прежнее решение",
                    Map.of(FIELD, REQUEST_ID));
        }
        try {
            return UUID.fromString(node.textValue());
        } catch (IllegalArgumentException rejected) {
            throw invalid(
                    "Поле 'request_id' = '" + node.textValue() + "' не является UUID: пример "
                            + "3f1c2d3e-0000-4000-8000-000000000000",
                    Map.of(FIELD, REQUEST_ID));
        }
    }

    private static int level(@Nullable JsonNode node) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()) {
            throw invalid("Поле 'level' обязательно и должно быть целым уровнем склада", Map.of(FIELD, LEVEL));
        }
        return node.intValue();
    }

    private static BlockCoord site(@Nullable JsonNode node) {
        if (node == null || !node.isTextual()) {
            throw invalid("Поле 'site' обязательно и должно быть строкой координат \"x,y,z\"", Map.of(FIELD, SITE));
        }
        String[] parts = node.textValue().split(",", -1);
        if (parts.length != 3) {
            throw invalid(
                    "Поле 'site' = '" + node.textValue() + "' не тройка координат: пример \"10,64,-3\"",
                    Map.of(FIELD, SITE));
        }
        try {
            return new BlockCoord(
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()));
        } catch (NumberFormatException notNumber) {
            throw invalid(
                    "Поле 'site' = '" + node.textValue() + "' содержит не целое число: пример \"10,64,-3\"",
                    Map.of(FIELD, SITE));
        }
    }

    private static String text(@Nullable JsonNode node, String field, String fallback) {
        if (node == null || node.isNull()) {
            return fallback;
        }
        if (!node.isTextual() || node.textValue().isBlank()) {
            throw invalid("Поле '" + field + "' должно быть непустой строкой", Map.of(FIELD, field));
        }
        return node.textValue();
    }

    private static JsonNode read(byte[] body) {
        try {
            return MAPPER.readTree(body);
        } catch (JsonProcessingException e) {
            JsonLocation location = e.getLocation();
            Map<String, Object> details = new LinkedHashMap<>();
            String where = "";
            if (location != null && location.getLineNr() > 0) {
                details.put("line", location.getLineNr());
                details.put("column", location.getColumnNr());
                where = " (строка " + location.getLineNr() + ", столбец " + location.getColumnNr() + ")";
            }
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "Тело POST settlement warehouse не разобрано как JSON" + where + ": " + e.getOriginalMessage()
                            + ". Проверьте скобки, кавычки и кодировку UTF-8",
                    details,
                    e);
        } catch (IOException e) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "Тело POST settlement warehouse не прочитано как JSON: " + e.getMessage(),
                    Map.of(),
                    e);
        }
    }

    private static void rejectUnknownFields(JsonNode root) {
        Iterator<String> names = root.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!FIELDS.contains(name)) {
                throw invalid(
                        "Неизвестное поле '" + name + "' в POST settlement warehouse: допустимы " + FIELDS,
                        Map.of(FIELD, name, "allowed", FIELDS));
            }
        }
    }

    private static ApiException invalid(String message, Map<String, Object> details) {
        return new ApiException(ErrorCode.INVALID_REQUEST, message, details);
    }
}
