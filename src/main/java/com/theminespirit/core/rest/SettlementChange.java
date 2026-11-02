package com.theminespirit.core.rest;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record SettlementChange(UUID requestId, int stage, String by, String reason) {
    public static final String DEFAULT_BY = "rest";
    public static final String DEFAULT_REASON = "откат этапа";

    private static final String FIELD = "field";
    private static final String REQUEST_ID = "request_id";
    private static final String STAGE = "stage";
    private static final String BY = "by";
    private static final String REASON = "reason";
    private static final List<String> FIELDS = List.of(REQUEST_ID, STAGE, BY, REASON);
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    public SettlementChange {
        if (requestId == null) {
            throw invalid(
                    "Поле 'request_id' обязательно: идемпотентность отката этапа держится на UUID клиента",
                    Map.of(FIELD, REQUEST_ID));
        }
        if (stage < 1) {
            throw invalid(
                    "Поле 'stage' = " + stage + " меньше 1: этапы поселения начинаются с первого",
                    Map.of(FIELD, STAGE));
        }
        if (by == null || by.isBlank()) {
            throw invalid("Поле 'by' пусто: по нему видно, кто откатил этап", Map.of(FIELD, BY));
        }
        if (reason == null || reason.isBlank()) {
            throw invalid("Поле 'reason' пусто: по причине видно, зачем откатили этап", Map.of(FIELD, REASON));
        }
    }

    public static SettlementChange parse(byte[] body) {
        JsonNode root = read(body);
        if (root == null || root.isNull() || !root.isObject()) {
            throw invalid(
                    "Тело POST settlement stage должно быть JSON-объектом вида {\"request_id\":\"<uuid>\","
                            + "\"stage\":1}, получено: " + (root == null ? "пустое тело" : root.getNodeType()),
                    Map.of("allowed", FIELDS));
        }
        rejectUnknownFields(root);
        return new SettlementChange(
                requestId(root.get(REQUEST_ID)),
                stage(root.get(STAGE)),
                text(root.get(BY), BY, DEFAULT_BY),
                text(root.get(REASON), REASON, DEFAULT_REASON));
    }

    private static UUID requestId(@Nullable JsonNode node) {
        if (node == null || !node.isTextual()) {
            throw invalid(
                    "Поле 'request_id' обязательно и должно быть строкой-UUID: повтор запроса с тем же "
                            + "request_id возвращает прежний откат",
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

    private static int stage(@Nullable JsonNode node) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()) {
            throw invalid(
                    "Поле 'stage' обязательно и должно быть целым числом этапа, например 1", Map.of(FIELD, STAGE));
        }
        return node.intValue();
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
                    "Тело POST settlement stage не разобрано как JSON" + where + ": " + e.getOriginalMessage()
                            + ". Проверьте скобки, кавычки и кодировку UTF-8",
                    details,
                    e);
        } catch (IOException e) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "Тело POST settlement stage не прочитано как JSON: " + e.getMessage(),
                    Map.of(),
                    e);
        }
    }

    private static void rejectUnknownFields(JsonNode root) {
        Iterator<String> names = root.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!FIELDS.contains(name)) {
                List<String> allowed = new ArrayList<>(FIELDS);
                throw invalid(
                        "Неизвестное поле '" + name + "' в POST settlement stage: допустимы " + allowed,
                        Map.of(FIELD, name, "allowed", allowed));
            }
        }
    }

    private static ApiException invalid(String message, Map<String, Object> details) {
        return new ApiException(ErrorCode.INVALID_REQUEST, message, details);
    }
}
