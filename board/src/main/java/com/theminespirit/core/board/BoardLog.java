package com.theminespirit.core.board;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class BoardLog {
    public enum Event {
        SUBMITTED,
        POSTED,
        ASSIGNED,
        DONE,
        FAILED,
        BLOCKED,
        UNBLOCKED,
        CANCELLED,
        DETACHED,
        REOPENED,
        NOTE
    }

    private static final int MAX_LINE_LENGTH = 8192;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private BoardLog() {}

    public static String culprit(List<BoardTicket> assigned) {
        Objects.requireNonNull(assigned, "Назначенные задания не могут быть null: пусто — пустой список");
        return assigned.isEmpty() ? "—" : assigned.get(0).id();
    }

    public static BoardLogEntry placement(
            String spirit, String task, String block, BlockCoord pos, String reason, long tick, String hash) {
        return note(
                "place:" + block,
                BoardCase.ACTIVITY_EXTERNAL,
                tick,
                List.of("task=" + task, "pos=" + pos.x() + "," + pos.y() + "," + pos.z(), "reason=" + reason),
                spirit,
                hash);
    }

    public static BoardLogEntry note(
            String id, BoardCase boardCase, long tick, List<String> details, @Nullable String spirit, String hash) {
        int priority = BoardRules.priorityOf(boardCase);
        return new BoardLogEntry(
                Event.NOTE, tick, id, boardCase, 1, id, priority, priority, details, spirit, hash, tick);
    }

    public static Map<String, Object> toMap(BoardLogEntry entry) {
        Objects.requireNonNull(entry, "Запись журнала не может быть null: писать нечего");
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("event", entry.event().name());
        map.put("tick", entry.tick());
        map.put("ticketId", entry.ticketId());
        map.put("case", entry.boardCase().name());
        map.put("level", entry.level());
        map.put("cause", entry.cause());
        map.put("basePriority", entry.basePriority());
        map.put("effectivePriority", entry.effectivePriority());
        map.put("modifiers", entry.modifiers());
        map.put("spirit", entry.spirit());
        map.put("configHash", entry.configHash());
        map.put("worldTime", entry.worldTime());
        return Collections.unmodifiableMap(map);
    }

    public static String write(BoardLogEntry entry) {
        Map<String, Object> map = toMap(entry);
        String json;
        try {
            json = MAPPER.writeValueAsString(map);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Запись журнала не сериализована: " + error.getOriginalMessage(), error);
        }
        if (json.length() > MAX_LINE_LENGTH) {
            throw new IllegalStateException("Строка журнала " + json.length() + " символов длиннее предела "
                    + MAX_LINE_LENGTH + ": одна запись обязана читаться одним проходом");
        }
        return json;
    }

    public static BoardLogEntry parse(String line) {
        return parse(line, 0);
    }

    public static BoardLogEntry parse(String line, int lineNumber) {
        if (line == null || line.isBlank()) {
            throw new IllegalStateException("Строка " + lineNumber + " журнала пуста: запись не разобрать");
        }
        if (line.length() > MAX_LINE_LENGTH) {
            throw new IllegalStateException("Строка " + lineNumber + " журнала " + line.length()
                    + " символов длиннее предела " + MAX_LINE_LENGTH + ": оборванная запись не "
                    + "должна молча становиться заданием");
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(line);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException(
                    "Строка " + lineNumber + " журнала не разобрана как JSON: " + error.getOriginalMessage(), error);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalStateException(
                    "Строка " + lineNumber + " журнала — не объект: запись " + "журнала ведётся объектом полей");
        }
        BoardLog.Event event = event(root, lineNumber);
        long tick = longField(root, "tick", lineNumber);
        String ticketId = text(root, "ticketId", lineNumber);
        BoardCase boardCase = boardCase(root, lineNumber);
        int level = intField(root, "level", lineNumber);
        String cause = text(root, "cause", lineNumber);
        int basePriority = intField(root, "basePriority", lineNumber);
        int effectivePriority = intField(root, "effectivePriority", lineNumber);
        List<String> modifiers = strings(root, "modifiers", lineNumber);
        String spirit = optionalText(root, "spirit", lineNumber);
        String configHash = text(root, "configHash", lineNumber);
        long worldTime = longField(root, "worldTime", lineNumber);
        try {
            return new BoardLogEntry(
                    event,
                    tick,
                    ticketId,
                    boardCase,
                    level,
                    cause,
                    basePriority,
                    effectivePriority,
                    modifiers,
                    spirit,
                    configHash,
                    worldTime);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException(
                    "Строка " + lineNumber + " журнала не прошла проверки: " + error.getMessage(), error);
        }
    }

    private static BoardLog.Event event(JsonNode root, int lineNumber) {
        JsonNode node = root.get("event");
        if (node == null || !node.isTextual()) {
            throw new IllegalStateException(
                    "Строка " + lineNumber + " журнала: поле 'event' обязательно " + "и должно быть именем события");
        }
        try {
            return BoardLog.Event.valueOf(node.textValue());
        } catch (IllegalArgumentException unknown) {
            throw new IllegalStateException(
                    "Строка " + lineNumber + " журнала: неизвестное событие '" + node.textValue() + "'", unknown);
        }
    }

    private static String text(JsonNode root, String field, int lineNumber) {
        JsonNode node = root.get(field);
        if (node == null || !node.isTextual() || node.textValue().isBlank()) {
            throw new IllegalStateException("Строка " + lineNumber + " журнала: поле '" + field
                    + "' обязательно и должно быть непустой строкой");
        }
        return node.textValue();
    }

    private static @Nullable String optionalText(JsonNode root, String field, int lineNumber) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw new IllegalStateException(
                    "Строка " + lineNumber + " журнала: поле '" + field + "' должно быть строкой или отсутствовать");
        }
        return node.textValue();
    }

    private static BoardCase boardCase(JsonNode root, int lineNumber) {
        String name = text(root, "case", lineNumber);
        try {
            return BoardCase.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            throw new IllegalStateException(
                    "Строка " + lineNumber + " журнала: неизвестный кейс '" + name + "'", unknown);
        }
    }

    private static int intField(JsonNode root, String field, int lineNumber) {
        JsonNode node = root.get(field);
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()) {
            throw new IllegalStateException(
                    "Строка " + lineNumber + " журнала: поле '" + field + "' обязано быть целым числом");
        }
        return node.intValue();
    }

    private static long longField(JsonNode root, String field, int lineNumber) {
        JsonNode node = root.get(field);
        if (node == null || !node.isIntegralNumber()) {
            throw new IllegalStateException(
                    "Строка " + lineNumber + " журнала: поле '" + field + "' обязано быть целым числом");
        }
        return node.longValue();
    }

    private static List<String> strings(JsonNode root, String field, int lineNumber) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new IllegalStateException(
                    "Строка " + lineNumber + " журнала: поле '" + field + "' должно быть массивом строк");
        }
        List<String> values = new ArrayList<>(node.size());
        for (JsonNode item : node) {
            if (!item.isTextual() || item.textValue().isBlank()) {
                throw new IllegalStateException("Строка " + lineNumber + " журнала: поле '" + field
                        + "' содержит пустую строку: сработавший модификатор назван именем");
            }
            values.add(item.textValue());
        }
        return List.copyOf(values);
    }
}
