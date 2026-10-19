package com.theminespirit.core.rest;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.theminespirit.core.observation.BlockCoord;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Тело POST /v1/policy/unprotect: куб мира, причина, кто попросил и срок жизни.
 * Id разрешения назначает мод; повтор с тем же {@code request_id} возвращает прежний ответ,
 * а не создаёт второе разрешение.
 */
public record UnprotectRequest(
        UUID requestId,
        String dimension,
        BlockCoord min,
        BlockCoord max,
        String grantedBy,
        String reason,
        long ttlSeconds) {

    private static final String REQUEST_ID = "request_id";
    private static final String DIMENSION = "dimension";
    private static final String MIN = "min";
    private static final String MAX = "max";
    private static final String GRANTED_BY = "granted_by";
    private static final String REASON = "reason";
    private static final String TTL_SECONDS = "ttl_seconds";
    private static final List<String> FIELDS =
            List.of(REQUEST_ID, DIMENSION, MIN, MAX, GRANTED_BY, REASON, TTL_SECONDS);
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    public UnprotectRequest {
        if (requestId == null) {
            throw invalid(
                    "Поле 'request_id' обязательно: идемпотентность снятия защиты держится на UUID клиента",
                    Map.of("field", REQUEST_ID));
        }
        if (dimension == null || dimension.isBlank()) {
            throw invalid(
                    "Поле 'dimension' обязательно: область принадлежит одному миру, например minecraft:overworld",
                    Map.of("field", DIMENSION));
        }
        if (min == null || max == null) {
            throw invalid(
                    "Углы области 'min' и 'max' обязательны: каждый — массив из трёх целых [x, y, z]",
                    Map.of("fields", List.of(MIN, MAX)));
        }
        if (grantedBy == null || grantedBy.isBlank()) {
            throw invalid(
                    "Поле 'granted_by' обязательно: по нему видно, кто попросил снять защиту",
                    Map.of("field", GRANTED_BY));
        }
        if (reason == null || reason.isBlank()) {
            throw invalid("Поле 'reason' обязательно: по причине видно, зачем сняли защиту", Map.of("field", REASON));
        }
        if (ttlSeconds < 0) {
            throw invalid(
                    "Поле 'ttl_seconds' = " + ttlSeconds + " отрицательно: бессрочное разрешение задают нулём",
                    Map.of("field", TTL_SECONDS));
        }
    }

    public static UnprotectRequest parse(byte[] body) {
        JsonNode root = read(body);
        if (root == null || root.isNull() || !root.isObject()) {
            throw invalid(
                    "Тело POST policy unprotect должно быть JSON-объектом вида {\"request_id\":\"<uuid>\","
                            + "\"dimension\":\"minecraft:overworld\",\"min\":[x,y,z],\"max\":[x,y,z],"
                            + "\"granted_by\":\"игрок\",\"reason\":\"убери постройку\",\"ttl_seconds\":3600}, получено: "
                            + (root == null ? "пустое тело" : root.getNodeType()),
                    Map.of("allowed", FIELDS));
        }
        rejectUnknownFields(root);
        return new UnprotectRequest(
                requestId(root.get(REQUEST_ID)),
                dimension(root.get(DIMENSION)),
                point(root.get(MIN), MIN),
                point(root.get(MAX), MAX),
                text(root.get(GRANTED_BY), GRANTED_BY),
                text(root.get(REASON), REASON),
                ttl(root.get(TTL_SECONDS)));
    }

    private static UUID requestId(JsonNode node) {
        if (node == null || !node.isTextual()) {
            throw invalid(
                    "Поле 'request_id' обязательно и должно быть строкой-UUID: повтор запроса с тем же "
                            + "request_id возвращает прежнее разрешение",
                    Map.of("field", REQUEST_ID));
        }
        try {
            return UUID.fromString(node.textValue());
        } catch (IllegalArgumentException rejected) {
            throw invalid(
                    "Поле 'request_id' = '" + node.textValue() + "' не является UUID: пример "
                            + "3f1c2d3e-0000-4000-8000-000000000000",
                    Map.of("field", REQUEST_ID));
        }
    }

    private static String dimension(JsonNode node) {
        if (node == null || !node.isTextual() || node.textValue().isBlank()) {
            throw invalid(
                    "Поле 'dimension' обязательно и должно быть непустой строкой вида minecraft:overworld",
                    Map.of("field", DIMENSION));
        }
        return node.textValue();
    }

    private static BlockCoord point(JsonNode node, String field) {
        if (node == null || !node.isArray() || node.size() != 3) {
            throw invalid(
                    "Поле '" + field + "' должно быть массивом из трёх целых [x, y, z], получено: "
                            + (node == null ? "нет" : node),
                    Map.of("field", field));
        }
        int[] values = new int[3];
        for (int i = 0; i < 3; i++) {
            JsonNode value = node.get(i);
            if (!value.isIntegralNumber() || !value.canConvertToInt()) {
                throw invalid(
                        "Координата " + i + " в '" + field + "' обязана быть целым числом, получено: " + value,
                        Map.of("field", field, "index", i));
            }
            values[i] = value.intValue();
        }
        return new BlockCoord(values[0], values[1], values[2]);
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.isTextual() || node.textValue().isBlank()) {
            throw invalid("Поле '" + field + "' обязательно и должно быть непустой строкой", Map.of("field", field));
        }
        return node.textValue();
    }

    private static long ttl(JsonNode node) {
        if (node == null || node.isNull()) {
            return 0;
        }
        if (!node.isIntegralNumber() || !node.canConvertToLong()) {
            throw invalid(
                    "Поле 'ttl_seconds' должно быть целым числом секунд, получено: " + node,
                    Map.of("field", TTL_SECONDS));
        }
        return node.longValue();
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
                    "Тело POST policy unprotect не разобрано как JSON" + where + ": " + e.getOriginalMessage()
                            + ". Проверьте скобки, кавычки и кодировку UTF-8",
                    details,
                    e);
        } catch (IOException e) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "Тело POST policy unprotect не прочитано как JSON: " + e.getMessage(),
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
                        "Неизвестное поле '" + name + "' в POST policy unprotect: допустимы " + FIELDS,
                        Map.of("field", name, "allowed", FIELDS));
            }
        }
    }

    private static ApiException invalid(String message, Map<String, Object> details) {
        return new ApiException(ErrorCode.INVALID_REQUEST, message, details);
    }
}
