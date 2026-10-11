package com.theminespirit.core.rest;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.theminespirit.core.observation.ScanRadius;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ObserveRequest(ScanRadius radius, List<String> filter, boolean includeEntities) {
    public static final int MAX_FILTER_PATTERNS = 16;
    public static final int MAX_FILTER_LENGTH = 64;

    private static final String RADIUS = "radius";
    private static final String FILTER = "filter";
    private static final String INCLUDE_ENTITIES = "include_entities";
    private static final List<String> FIELDS = List.of(RADIUS, FILTER, INCLUDE_ENTITIES);
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    public ObserveRequest {
        filter = List.copyOf(filter);
    }

    public static ObserveRequest defaults() {
        return new ObserveRequest(ScanRadius.DEFAULT, List.of(), true);
    }

    public static ObserveRequest parse(byte[] body) {
        if (isBlank(body)) {
            return defaults();
        }
        JsonNode root = read(body);
        if (!root.isObject()) {
            throw invalid("Тело запроса observe должно быть JSON-объектом вида "
                    + "{\"radius\": 8, \"filter\": [\"*_ore\"], \"include_entities\": true}, получено: " + root.getNodeType(),
                    Map.of("allowed", FIELDS));
        }
        rejectUnknownFields(root);
        ScanRadius radius = root.has(RADIUS) ? radius(root.get(RADIUS)) : ScanRadius.DEFAULT;
        List<String> filter = root.has(FILTER) ? filter(root.get(FILTER)) : List.of();
        boolean includeEntities = !root.has(INCLUDE_ENTITIES) || includeEntities(root.get(INCLUDE_ENTITIES));
        return new ObserveRequest(radius, filter, includeEntities);
    }

    private static boolean isBlank(byte[] body) {
        for (byte b : body) {
            if (b != ' ' && b != '\t' && b != '\r' && b != '\n') {
                return false;
            }
        }
        return true;
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
                    "Тело запроса не разобрано как JSON" + where + ": " + e.getOriginalMessage()
                            + ". Проверьте скобки, кавычки и кодировку UTF-8",
                    details, e);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Тело запроса не прочитано как JSON: " + e.getMessage(), Map.of(), e);
        }
    }

    private static void rejectUnknownFields(JsonNode root) {
        Iterator<String> names = root.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!FIELDS.contains(name)) {
                throw invalid("Неизвестное поле '" + name + "' в запросе observe: допустимы " + FIELDS,
                        Map.of("field", name, "allowed", FIELDS));
            }
        }
    }

    private static ScanRadius radius(JsonNode node) {
        if (!node.isInt()) {
            throw invalid("Поле 'radius' должно быть целым числом от " + ScanRadius.MIN + " до " + ScanRadius.MAX
                    + ", получено: " + node, Map.of("field", RADIUS, "min", ScanRadius.MIN, "max", ScanRadius.MAX));
        }
        int value = node.intValue();
        if (value < ScanRadius.MIN || value > ScanRadius.MAX) {
            throw invalid("Поле 'radius' = " + value + " вне диапазона [" + ScanRadius.MIN + ", " + ScanRadius.MAX
                    + "]: больший скан нагружает серверный тик", Map.of("field", RADIUS, "min", ScanRadius.MIN,
                            "max", ScanRadius.MAX));
        }
        return ScanRadius.of(value);
    }

    private static List<String> filter(JsonNode node) {
        if (!node.isArray()) {
            throw invalid("Поле 'filter' должно быть массивом строк-шаблонов, например [\"*_ore\"], получено: "
                    + node.getNodeType(), Map.of("field", FILTER));
        }
        if (node.size() > MAX_FILTER_PATTERNS) {
            throw invalid("Поле 'filter' содержит " + node.size() + " шаблонов, допустимо не больше "
                    + MAX_FILTER_PATTERNS, Map.of("field", FILTER, "max", MAX_FILTER_PATTERNS));
        }
        List<String> patterns = new ArrayList<>(node.size());
        for (int i = 0; i < node.size(); i++) {
            patterns.add(pattern(node.get(i), i));
        }
        return patterns;
    }

    private static String pattern(JsonNode element, int index) {
        if (!element.isTextual() || element.textValue().isBlank()) {
            throw invalid("Элемент filter[" + index + "] должен быть непустой строкой-шаблоном, получено: " + element,
                    Map.of("field", FILTER, "index", index));
        }
        String pattern = element.textValue();
        if (pattern.length() > MAX_FILTER_LENGTH) {
            throw invalid("Шаблон filter[" + index + "] длиной " + pattern.length() + " длиннее допустимых "
                    + MAX_FILTER_LENGTH + " символов", Map.of("field", FILTER, "index", index,
                            "max_length", MAX_FILTER_LENGTH));
        }
        return pattern;
    }

    private static boolean includeEntities(JsonNode node) {
        if (!node.isBoolean()) {
            throw invalid("Поле 'include_entities' должно быть true или false, получено: " + node,
                    Map.of("field", INCLUDE_ENTITIES));
        }
        return node.booleanValue();
    }

    private static ApiException invalid(String message, Map<String, Object> details) {
        return new ApiException(ErrorCode.INVALID_REQUEST, message, details);
    }
}
