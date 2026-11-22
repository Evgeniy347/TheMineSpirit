package com.theminespirit.core.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.board.BoardActivities;
import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

final class BoardTaskFields {
    private BoardTaskFields() {}

    static UUID requestId(JsonNode node) {
        if (node == null || !node.isTextual()) {
            throw BoardTaskRequest.invalid(
                    "Поле 'request_id' обязательно и должно быть строкой-UUID: повтор запроса с тем же "
                            + "request_id возвращает прежнее задание",
                    Map.of(BoardTaskRequest.FIELD, BoardTaskRequest.REQUEST_ID));
        }
        try {
            return UUID.fromString(node.textValue());
        } catch (IllegalArgumentException rejected) {
            throw BoardTaskRequest.invalid(
                    "Поле 'request_id' = '" + node.textValue() + "' не является UUID: пример "
                            + "3f1c2d3e-0000-4000-8000-000000000000",
                    Map.of(BoardTaskRequest.FIELD, BoardTaskRequest.REQUEST_ID));
        }
    }

    static BoardCase boardCase(JsonNode node) {
        if (node == null || !node.isTextual()) {
            throw BoardTaskRequest.invalid(
                    "Поле 'case' обязательно и должно быть строкой-кейсом: " + BoardTaskRequest.caseNames(),
                    Map.of(
                            BoardTaskRequest.FIELD,
                            BoardTaskRequest.CASE,
                            BoardTaskRequest.ALLOWED,
                            BoardTaskRequest.caseNames()));
        }
        try {
            BoardCase parsed = BoardCase.valueOf(node.textValue().toUpperCase(Locale.ROOT));
            if (BoardActivities.isActivity(parsed)) {
                throw new IllegalArgumentException("Занятие духа показывает доска сама, заданием его не поставить");
            }
            return parsed;
        } catch (IllegalArgumentException unknown) {
            throw new ApiException(
                    ErrorCode.INVALID_PLAN,
                    "Кейс '" + node.textValue() + "' не известен доске: допустимы " + BoardTaskRequest.caseNames(),
                    Map.of(
                            BoardTaskRequest.FIELD,
                            BoardTaskRequest.CASE,
                            BoardTaskRequest.ALLOWED,
                            BoardTaskRequest.caseNames()));
        }
    }

    static Optional<UUID> assignee(@Nullable JsonNode node) {
        if (node == null || node.isNull()) {
            return Optional.empty();
        }
        if (!node.isTextual()) {
            throw BoardTaskRequest.invalid(
                    "Поле 'assignee' должно быть строкой-UUID или отсутствовать: пустое значение значит "
                            + "«любой свободный дух», получено: " + node.getNodeType(),
                    Map.of(BoardTaskRequest.FIELD, BoardTaskRequest.ASSIGNEE));
        }
        try {
            return Optional.of(UUID.fromString(node.textValue()));
        } catch (IllegalArgumentException rejected) {
            throw BoardTaskRequest.invalid(
                    "Поле 'assignee' = '" + node.textValue() + "' не является UUID: адресный владелец "
                            + "задаётся UUID духа",
                    Map.of(BoardTaskRequest.FIELD, BoardTaskRequest.ASSIGNEE));
        }
    }

    static Map<String, Integer> items(@Nullable JsonNode node) {
        if (node == null || node.isNull()) {
            return Map.of();
        }
        if (!node.isObject()) {
            throw BoardTaskRequest.invalid(
                    "Поле 'requiredItems' должно быть JSON-объектом «предмет: количество», получено: "
                            + node.getNodeType(),
                    Map.of(BoardTaskRequest.FIELD, BoardTaskRequest.REQUIRED_ITEMS));
        }
        Map<String, Integer> items = new LinkedHashMap<>();
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            JsonNode value = node.get(name);
            if (name.isBlank()) {
                throw BoardTaskRequest.invalid(
                        "Предмет в 'requiredItems' назван пустой строкой: укажите id предмета, " + "например oak_log",
                        Map.of(BoardTaskRequest.FIELD, BoardTaskRequest.REQUIRED_ITEMS));
            }
            if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 0) {
                throw BoardTaskRequest.invalid(
                        "Количество предмета '" + name + "' обязано быть неотрицательным целым, получено: " + value,
                        Map.of(BoardTaskRequest.FIELD, BoardTaskRequest.REQUIRED_ITEMS, "item", name));
            }
            items.put(name, value.intValue());
        }
        return Map.copyOf(items);
    }

    static List<String> dependsOn(@Nullable JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw BoardTaskRequest.invalid(
                    "Поле 'dependsOn' должно быть массивом id заданий, получено: " + node.getNodeType(),
                    Map.of(BoardTaskRequest.FIELD, BoardTaskRequest.DEPENDS_ON));
        }
        List<String> ids = new ArrayList<>();
        for (JsonNode element : node) {
            if (!element.isTextual() || element.textValue().isBlank()) {
                throw BoardTaskRequest.invalid(
                        "Каждая зависимость в 'dependsOn' обязана быть непустой строкой-id задания, " + "получено: "
                                + element,
                        Map.of(BoardTaskRequest.FIELD, BoardTaskRequest.DEPENDS_ON));
            }
            ids.add(element.textValue());
        }
        return List.copyOf(ids);
    }

    static int level(@Nullable JsonNode node) {
        if (node == null || node.isNull()) {
            return 1;
        }
        if (!node.isIntegralNumber() || !node.canConvertToInt()) {
            throw BoardTaskRequest.invalid(
                    "Поле 'level' должно быть целым числом уровня постройки, получено: " + node.getNodeType(),
                    Map.of(BoardTaskRequest.FIELD, BoardTaskRequest.LEVEL));
        }
        int level = node.intValue();
        if (level < 1) {
            throw BoardTaskRequest.invalid(
                    "Поле 'level' = " + level + " меньше 1: первая постройка ставится " + "уровнем 1",
                    Map.of(BoardTaskRequest.FIELD, BoardTaskRequest.LEVEL));
        }
        return level;
    }

    static Optional<BoardCase> building(@Nullable JsonNode node) {
        if (node == null || node.isNull()) {
            return Optional.empty();
        }
        if (!node.isTextual()) {
            throw BoardTaskRequest.invalid(
                    "Поле 'building' должно быть строкой-кейсом постройки, получено: " + node.getNodeType(),
                    Map.of(
                            BoardTaskRequest.FIELD,
                            BoardTaskRequest.BUILDING,
                            BoardTaskRequest.ALLOWED,
                            BoardTaskRequest.caseNames()));
        }
        try {
            return Optional.of(BoardCase.valueOf(node.textValue().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException unknown) {
            throw BoardTaskRequest.invalid(
                    "Кейс постройки '" + node.textValue() + "' неизвестен: допустимы " + BoardTaskRequest.caseNames(),
                    Map.of(
                            BoardTaskRequest.FIELD,
                            BoardTaskRequest.BUILDING,
                            BoardTaskRequest.ALLOWED,
                            BoardTaskRequest.caseNames()));
        }
    }

    static Optional<BlockCoord> point(@Nullable JsonNode node, String field) {
        if (node == null || node.isNull()) {
            return Optional.empty();
        }
        if (!node.isTextual()) {
            throw BoardTaskRequest.invalid(
                    "Поле '" + field + "' должно быть строкой координат \"x,y,z\", получено: " + node.getNodeType(),
                    Map.of(BoardTaskRequest.FIELD, field));
        }
        String[] parts = node.textValue().split(",", -1);
        if (parts.length != 3) {
            throw BoardTaskRequest.invalid(
                    "Поле '" + field + "' = '" + node.textValue() + "' не тройка координат: " + "пример \"10,64,-3\"",
                    Map.of(BoardTaskRequest.FIELD, field));
        }
        try {
            return Optional.of(new BlockCoord(
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim())));
        } catch (NumberFormatException notNumber) {
            throw BoardTaskRequest.invalid(
                    "Поле '" + field + "' = '" + node.textValue() + "' содержит не целое число: "
                            + "пример \"10,64,-3\"",
                    Map.of(BoardTaskRequest.FIELD, field));
        }
    }

    static @Nullable String title(@Nullable JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw BoardTaskRequest.invalid(
                    "Поле 'title' должно быть строкой названия задания, получено: " + node.getNodeType(),
                    Map.of(BoardTaskRequest.FIELD, BoardTaskRequest.TITLE));
        }
        String text = node.textValue().trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.length() > BoardTaskRequest.MAX_TITLE_LENGTH) {
            throw BoardTaskRequest.invalid(
                    "Название задания длиннее " + BoardTaskRequest.MAX_TITLE_LENGTH + " символов (" + text.length()
                            + "): назовите задание короче",
                    Map.of(
                            BoardTaskRequest.FIELD,
                            BoardTaskRequest.TITLE,
                            "max_length",
                            BoardTaskRequest.MAX_TITLE_LENGTH));
        }
        return text;
    }

    static String cause(@Nullable JsonNode node) {
        if (node == null || node.isNull()) {
            return "always";
        }
        if (!node.isTextual() || node.textValue().isBlank()) {
            throw BoardTaskRequest.invalid(
                    "Поле 'cause' обязано быть непустой строкой-ключом причины "
                            + "(«always» для постоянных), получено: " + node,
                    Map.of(BoardTaskRequest.FIELD, BoardTaskRequest.CAUSE));
        }
        return node.textValue();
    }
}
