package com.theminespirit.core.rest;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record TaskRequest(UUID requestId, String goal, JsonNode plan, Map<String, Object> params) {
    public static final int MAX_GOAL_LENGTH = 500;

    private static final String REQUEST_ID = "request_id";
    private static final String GOAL = "goal";
    private static final String PLAN = "plan";
    private static final String PARAMS = "params";
    private static final List<String> FIELDS = List.of(REQUEST_ID, GOAL, PLAN, PARAMS);
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    public TaskRequest {
        Objects.requireNonNull(requestId, "requestId не может быть null: идемпотентность требует UUID клиента");
        params = Map.copyOf(Objects.requireNonNull(params,
                "params не может быть null: пустые параметры задают пустой картой"));
        if (goal == null && plan == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Тело POST tasks обязано содержать goal или plan: без цели и без плана нечего выполнять",
                    Map.of("allowed", FIELDS));
        }
    }

    public static TaskRequest parse(byte[] body) {
        JsonNode root = read(body);
        if (root == null || root.isNull() || !root.isObject()) {
            throw invalid("Тело POST tasks должно быть JSON-объектом вида "
                    + "{\"request_id\":\"<uuid>\",\"goal\":\"…\",\"plan\":{…},\"params\":{…}}, получено: "
                    + (root == null ? "пустое тело" : root.getNodeType()), Map.of("allowed", FIELDS));
        }
        rejectUnknownFields(root);
        UUID requestId = requestId(root.get(REQUEST_ID));
        String goal = textOrNull(root, GOAL);
        JsonNode plan = objectOrNull(root, PLAN);
        Map<String, Object> params = root.has(PARAMS) && !root.get(PARAMS).isNull()
                ? params(root.get(PARAMS)) : Map.of();
        return new TaskRequest(requestId, goal, plan, params);
    }

    private static String textOrNull(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node == null || node.isNull() ? null : goal(node);
    }

    private static JsonNode objectOrNull(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node == null || node.isNull() ? null : plan(node);
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
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Тело POST tasks не разобрано как JSON" + where + ": " + e.getOriginalMessage()
                            + ". Проверьте скобки, кавычки и кодировку UTF-8",
                    details, e);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Тело POST tasks не прочитано как JSON: " + e.getMessage(), Map.of(), e);
        }
    }

    private static void rejectUnknownFields(JsonNode root) {
        Iterator<String> names = root.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!FIELDS.contains(name)) {
                throw invalid("Неизвестное поле '" + name + "' в POST tasks: допустимы " + FIELDS,
                        Map.of("field", name, "allowed", FIELDS));
            }
        }
    }

    private static UUID requestId(JsonNode node) {
        if (node == null || !node.isTextual()) {
            throw invalid("Поле 'request_id' обязательно и должно быть строкой-UUID: повтор запроса с тем же "
                    + "request_id возвращает прежнюю задачу", Map.of("field", REQUEST_ID));
        }
        try {
            return UUID.fromString(node.textValue());
        } catch (IllegalArgumentException rejected) {
            throw invalid("Поле 'request_id' = '" + node.textValue() + "' не является UUID: пример "
                    + "3f1c2d3e-0000-4000-8000-000000000000", Map.of("field", REQUEST_ID));
        }
    }

    private static String goal(JsonNode node) {
        if (!node.isTextual()) {
            throw invalid("Поле 'goal' должно быть строкой, получено: " + node.getNodeType(),
                    Map.of("field", GOAL));
        }
        String text = node.textValue();
        if (text.length() > MAX_GOAL_LENGTH) {
            throw invalid("Цель длиннее " + MAX_GOAL_LENGTH + " символов (" + text.length()
                    + "): опишите цель короче или передайте план", Map.of("field", GOAL, "max_length", MAX_GOAL_LENGTH));
        }
        return text;
    }

    private static JsonNode plan(JsonNode node) {
        if (!node.isObject()) {
            throw invalid("Поле 'plan' должно быть JSON-объектом дерева задач, получено: " + node.getNodeType(),
                    Map.of("field", PLAN));
        }
        return node;
    }

    private static Map<String, Object> params(JsonNode node) {
        if (!node.isObject()) {
            throw invalid("Поле 'params' должно быть JSON-объектом строк и целых, получено: " + node.getNodeType(),
                    Map.of("field", PARAMS));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            JsonNode value = node.get(name);
            if (value.isTextual()) {
                result.put(name, value.textValue());
            } else if (value.isIntegralNumber() && value.canConvertToInt()) {
                result.put(name, value.intValue());
            } else {
                throw invalid("Параметр '" + name + "' обязан быть строкой или целым, получено: " + value,
                        Map.of("field", PARAMS, "param", name));
            }
        }
        return Map.copyOf(result);
    }

    private static ApiException invalid(String message, Map<String, Object> details) {
        return new ApiException(ErrorCode.INVALID_REQUEST, message, details);
    }
}
