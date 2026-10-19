package com.theminespirit.core.rest;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.RelocatePlan;
import com.theminespirit.core.observation.BlockCoord;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Тело POST /v1/board/tasks: кейс доски, владелец, гейт, зависимости, уровень и причина.
 * Id задания доска назначает сама — стратег ссылается на него из {@code dependsOn} после
 * постановки. Неизвестный кейс отклоняется со списком допустимых: опечатка в имени кейса
 * не должна тихо ставить задание-сироту.
 */
public record BoardTaskRequest(
        UUID requestId,
        BoardCase boardCase,
        Optional<UUID> assignee,
        Map<String, Integer> requiredItems,
        List<String> dependsOn,
        int level,
        String cause,
        Optional<BoardCase> building,
        Optional<BlockCoord> from,
        Optional<BlockCoord> to) {

    private static final String REQUEST_ID = "request_id";
    private static final String CASE = "case";
    private static final String ASSIGNEE = "assignee";
    private static final String REQUIRED_ITEMS = "requiredItems";
    private static final String DEPENDS_ON = "dependsOn";
    private static final String LEVEL = "level";
    private static final String CAUSE = "cause";
    private static final String BUILDING = "building";
    private static final String FROM = "from";
    private static final String TO = "to";
    private static final List<String> FIELDS =
            List.of(REQUEST_ID, CASE, ASSIGNEE, REQUIRED_ITEMS, DEPENDS_ON, LEVEL, CAUSE, BUILDING, FROM, TO);
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    public BoardTaskRequest(
            UUID requestId,
            BoardCase boardCase,
            Optional<UUID> assignee,
            Map<String, Integer> requiredItems,
            List<String> dependsOn,
            int level,
            String cause) {
        this(
                requestId,
                boardCase,
                assignee,
                requiredItems,
                dependsOn,
                level,
                cause,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    public BoardTaskRequest {
        if (requestId == null) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "request_id обязателен: идемпотентность постановки держится на UUID клиента",
                    Map.of("field", REQUEST_ID));
        }
        if (boardCase == null) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "Поле 'case' обязательно: без кейса доска не знает приоритет, гейт и план задания",
                    Map.of("field", CASE, "allowed", caseNames()));
        }
        if (level < 1) {
            throw invalid(
                    "Поле 'level' = " + level + " меньше 1: первая постройка ставится "
                            + "уровнем 1, отсутствующий уровень задают единицей",
                    Map.of("field", LEVEL));
        }
        if (cause == null || cause.isBlank()) {
            throw invalid(
                    "Поле 'cause' пусто: по причине пересборка проверяет актуальность, "
                            + "постоянное задание задают причиной always",
                    Map.of("field", CAUSE));
        }
        assignee = assignee == null ? Optional.empty() : assignee;
        requiredItems = Map.copyOf(requiredItems == null ? Map.of() : requiredItems);
        dependsOn = List.copyOf(dependsOn == null ? List.of() : dependsOn);
        building = building == null ? Optional.empty() : building;
        from = from == null ? Optional.empty() : from;
        to = to == null ? Optional.empty() : to;
        if (boardCase == BoardCase.TRANSFER_ITEM) {
            throw invalid(
                    "Кейс TRANSFER_ITEM ставит доска по нехватке ингредиентов у крафтера: "
                            + "вручную он не принимается, иначе план не знает ни предмета, ни получателя",
                    Map.of("field", CASE, "case", boardCase.name()));
        }
        if (boardCase == BoardCase.RELOCATE_BUILDING) {
            requireRelocation(building, from, to);
        } else if (building.isPresent() || from.isPresent() || to.isPresent()) {
            throw invalid(
                    "Поля 'building', 'from' и 'to' относятся только к RELOCATE_BUILDING: " + "у кейса " + boardCase
                            + " их быть не должно",
                    Map.of("field", boardCase == BoardCase.RELOCATE_BUILDING ? FROM : BUILDING));
        }
    }

    private static void requireRelocation(
            Optional<BoardCase> building, Optional<BlockCoord> from, Optional<BlockCoord> to) {
        if (building.isEmpty() || from.isEmpty() || to.isEmpty()) {
            throw invalid(
                    "Перенос требует полей 'building' (что переносим), 'from' и 'to' (точки): "
                            + "без них неизвестно, какую постройку сносить и где ставить",
                    Map.of("allowed", List.of(BUILDING, FROM, TO)));
        }
        if (from.orElseThrow().equals(to.orElseThrow())) {
            throw invalid(
                    "Перенос из точки " + from.orElseThrow() + " в ту же точку: "
                            + "сносить и строить на одном месте нечего",
                    Map.of("field", TO));
        }
        try {
            RelocatePlan.assertMovable(building.orElseThrow());
        } catch (IllegalArgumentException rejected) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    422,
                    rejected.getMessage(),
                    Map.of("building", building.orElseThrow().name(), "immovable", RelocatePlan.immovableNames()));
        }
    }

    public static List<String> caseNames() {
        return Arrays.stream(BoardCase.values()).map(BoardCase::name).toList();
    }

    public static BoardTaskRequest parse(byte[] body) {
        JsonNode root = read(body);
        if (root == null || root.isNull() || !root.isObject()) {
            throw invalid(
                    "Тело POST board tasks должно быть JSON-объектом вида "
                            + "{\"request_id\":\"<uuid>\",\"case\":\"BUILD_FENCE\",\"assignee\":\"<uuid>\","
                            + "\"requiredItems\":{\"oak_log\":16},\"dependsOn\":[\"…\"],\"level\":1,"
                            + "\"cause\":\"always\"}, получено: "
                            + (root == null ? "пустое тело" : root.getNodeType()),
                    Map.of("allowed", FIELDS));
        }
        rejectUnknownFields(root);
        UUID requestId = requestId(root.get(REQUEST_ID));
        BoardCase boardCase = boardCase(root.get(CASE));
        Optional<UUID> assignee = assignee(root.get(ASSIGNEE));
        Map<String, Integer> requiredItems = items(root.get(REQUIRED_ITEMS));
        List<String> dependsOn = dependsOn(root.get(DEPENDS_ON));
        int level = level(root.get(LEVEL));
        String cause = cause(root.get(CAUSE));
        Optional<BoardCase> building = building(root.get(BUILDING));
        Optional<BlockCoord> from = point(root.get(FROM), FROM);
        Optional<BlockCoord> to = point(root.get(TO), TO);
        return new BoardTaskRequest(
                requestId, boardCase, assignee, requiredItems, dependsOn, level, cause, building, from, to);
    }

    private static UUID requestId(JsonNode node) {
        if (node == null || !node.isTextual()) {
            throw invalid(
                    "Поле 'request_id' обязательно и должно быть строкой-UUID: повтор запроса с тем же "
                            + "request_id возвращает прежнее задание",
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

    private static BoardCase boardCase(JsonNode node) {
        if (node == null || !node.isTextual()) {
            throw invalid(
                    "Поле 'case' обязательно и должно быть строкой-кейсом: " + caseNames(),
                    Map.of("field", CASE, "allowed", caseNames()));
        }
        try {
            return BoardCase.valueOf(node.textValue().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new ApiException(
                    ErrorCode.INVALID_PLAN,
                    "Кейс '" + node.textValue() + "' не известен доске: допустимы " + caseNames(),
                    Map.of("field", CASE, "allowed", caseNames()));
        }
    }

    private static Optional<UUID> assignee(JsonNode node) {
        if (node == null || node.isNull()) {
            return Optional.empty();
        }
        if (!node.isTextual()) {
            throw invalid(
                    "Поле 'assignee' должно быть строкой-UUID или отсутствовать: пустое значение значит "
                            + "«любой свободный дух», получено: " + node.getNodeType(),
                    Map.of("field", ASSIGNEE));
        }
        try {
            return Optional.of(UUID.fromString(node.textValue()));
        } catch (IllegalArgumentException rejected) {
            throw invalid(
                    "Поле 'assignee' = '" + node.textValue() + "' не является UUID: адресный владелец "
                            + "задаётся UUID духа",
                    Map.of("field", ASSIGNEE));
        }
    }

    private static Map<String, Integer> items(JsonNode node) {
        if (node == null || node.isNull()) {
            return Map.of();
        }
        if (!node.isObject()) {
            throw invalid(
                    "Поле 'requiredItems' должно быть JSON-объектом «предмет: количество», получено: "
                            + node.getNodeType(),
                    Map.of("field", REQUIRED_ITEMS));
        }
        Map<String, Integer> items = new LinkedHashMap<>();
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            JsonNode value = node.get(name);
            if (name.isBlank()) {
                throw invalid(
                        "Предмет в 'requiredItems' назван пустой строкой: укажите id предмета, " + "например oak_log",
                        Map.of("field", REQUIRED_ITEMS));
            }
            if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 0) {
                throw invalid(
                        "Количество предмета '" + name + "' обязано быть неотрицательным целым, получено: " + value,
                        Map.of("field", REQUIRED_ITEMS, "item", name));
            }
            items.put(name, value.intValue());
        }
        return Map.copyOf(items);
    }

    private static List<String> dependsOn(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw invalid(
                    "Поле 'dependsOn' должно быть массивом id заданий, получено: " + node.getNodeType(),
                    Map.of("field", DEPENDS_ON));
        }
        List<String> ids = new ArrayList<>();
        for (JsonNode element : node) {
            if (!element.isTextual() || element.textValue().isBlank()) {
                throw invalid(
                        "Каждая зависимость в 'dependsOn' обязана быть непустой строкой-id задания, " + "получено: "
                                + element,
                        Map.of("field", DEPENDS_ON));
            }
            ids.add(element.textValue());
        }
        return List.copyOf(ids);
    }

    private static int level(JsonNode node) {
        if (node == null || node.isNull()) {
            return 1;
        }
        if (!node.isIntegralNumber() || !node.canConvertToInt()) {
            throw invalid(
                    "Поле 'level' должно быть целым числом уровня постройки, получено: " + node.getNodeType(),
                    Map.of("field", LEVEL));
        }
        int level = node.intValue();
        if (level < 1) {
            throw invalid(
                    "Поле 'level' = " + level + " меньше 1: первая постройка ставится " + "уровнем 1",
                    Map.of("field", LEVEL));
        }
        return level;
    }

    private static Optional<BoardCase> building(JsonNode node) {
        if (node == null || node.isNull()) {
            return Optional.empty();
        }
        if (!node.isTextual()) {
            throw invalid(
                    "Поле 'building' должно быть строкой-кейсом постройки, получено: " + node.getNodeType(),
                    Map.of("field", BUILDING, "allowed", caseNames()));
        }
        try {
            return Optional.of(BoardCase.valueOf(node.textValue().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException unknown) {
            throw invalid(
                    "Кейс постройки '" + node.textValue() + "' неизвестен: допустимы " + caseNames(),
                    Map.of("field", BUILDING, "allowed", caseNames()));
        }
    }

    private static Optional<BlockCoord> point(JsonNode node, String field) {
        if (node == null || node.isNull()) {
            return Optional.empty();
        }
        if (!node.isTextual()) {
            throw invalid(
                    "Поле '" + field + "' должно быть строкой координат \"x,y,z\", получено: " + node.getNodeType(),
                    Map.of("field", field));
        }
        String[] parts = node.textValue().split(",");
        if (parts.length != 3) {
            throw invalid(
                    "Поле '" + field + "' = '" + node.textValue() + "' не тройка координат: " + "пример \"10,64,-3\"",
                    Map.of("field", field));
        }
        try {
            return Optional.of(new BlockCoord(
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim())));
        } catch (NumberFormatException notNumber) {
            throw invalid(
                    "Поле '" + field + "' = '" + node.textValue() + "' содержит не целое число: "
                            + "пример \"10,64,-3\"",
                    Map.of("field", field));
        }
    }

    private static String cause(JsonNode node) {
        if (node == null || node.isNull()) {
            return "always";
        }
        if (!node.isTextual() || node.textValue().isBlank()) {
            throw invalid(
                    "Поле 'cause' обязано быть непустой строкой-ключом причины "
                            + "(«always» для постоянных), получено: " + node,
                    Map.of("field", CAUSE));
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
                    "Тело POST board tasks не разобрано как JSON" + where + ": " + e.getOriginalMessage()
                            + ". Проверьте скобки, кавычки и кодировку UTF-8",
                    details,
                    e);
        } catch (IOException e) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "Тело POST board tasks не прочитано как JSON: " + e.getMessage(),
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
                        "Неизвестное поле '" + name + "' в POST board tasks: допустимы " + FIELDS,
                        Map.of("field", name, "allowed", FIELDS));
            }
        }
    }

    private static ApiException invalid(String message, Map<String, Object> details) {
        return new ApiException(ErrorCode.INVALID_REQUEST, message, details);
    }
}
