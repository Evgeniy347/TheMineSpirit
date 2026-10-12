package com.theminespirit.core.perception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class MemoryCodec {
    public static final int VERSION = 1;
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private MemoryCodec() {
    }

    public static String encode(MemoryStore store) {
        Objects.requireNonNull(store, "store не может быть null: сохранять нечего");
        ObjectNode root = MAPPER.createObjectNode();
        root.put("version", VERSION);
        ArrayNode points = root.putArray("points");
        for (MemoryPoint point : store.points()) {
            ObjectNode node = points.addObject();
            node.put("name", point.name());
            node.put("type", point.type().id());
            node.put("dimension", point.dimension());
            node.put("x", point.pos().x());
            node.put("y", point.pos().y());
            node.put("z", point.pos().z());
        }
        try {
            return MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Память духа не сериализована в JSON: проверьте значения точек", e);
        }
    }

    public static MemoryStore decode(String json) {
        Objects.requireNonNull(json, "json не может быть null: память приходит текстом из ключа SpiritMemory");
        JsonNode root = readTree(json);
        if (!root.isObject()) {
            throw new MemoryFormatException("Память духа должна быть JSON-объектом, а получено " + root
                    + ": проверьте ключ SpiritMemory, файл не правьте вручную");
        }
        requireVersion(root);
        JsonNode pointsNode = root.get("points");
        if (pointsNode == null || !pointsNode.isArray()) {
            throw new MemoryFormatException("У памяти духа нет массива points: без него неизвестно, что духу помнить");
        }
        List<MemoryPoint> points = new ArrayList<>(pointsNode.size());
        for (JsonNode node : pointsNode) {
            points.add(parsePoint(node));
        }
        try {
            return new MemoryStore(points);
        } catch (IllegalArgumentException | MemoryFullException e) {
            throw new MemoryFormatException("Набор точек памяти недопустим: " + e.getMessage(), e);
        }
    }

    private static JsonNode readTree(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new MemoryFormatException("Память духа не разобрана как JSON: проверьте синтаксис значения "
                    + "ключа SpiritMemory, файл не правьте вручную", e);
        }
    }

    private static void requireVersion(JsonNode root) {
        JsonNode versionNode = root.get("version");
        if (versionNode == null || !versionNode.isIntegralNumber() || !versionNode.canConvertToInt()) {
            throw new MemoryFormatException("У памяти духа нет целого поля version: без версии читатель "
                    + "не выберет разбор формата");
        }
        int version = versionNode.intValue();
        if (version > VERSION) {
            throw new MemoryFormatException("Версия памяти " + version + " новее поддерживаемой " + VERSION
                    + ": обнови мод или добавь читателя старой версии, файл не правьте вручную");
        }
        if (version < 1) {
            throw new MemoryFormatException("Версия памяти " + version + " меньше 1: допустимая версия начинается с 1");
        }
    }

    private static MemoryPoint parsePoint(JsonNode node) {
        if (!node.isObject()) {
            throw new MemoryFormatException("Точка памяти должна быть JSON-объектом, а получено " + node);
        }
        String name = requireText(node, "name");
        MemoryType type = parseType(requireText(node, "type"), name);
        BlockCoord pos = new BlockCoord(requireInt(node, "x", name), requireInt(node, "y", name),
                requireInt(node, "z", name));
        try {
            return new MemoryPoint(name, type, requireText(node, "dimension"), pos);
        } catch (IllegalArgumentException e) {
            throw new MemoryFormatException("Точка памяти '" + name + "' недопустима: " + e.getMessage(), e);
        }
    }

    private static MemoryType parseType(String id, String pointName) {
        try {
            return MemoryType.parse(id);
        } catch (IllegalArgumentException e) {
            throw new MemoryFormatException("У точки памяти '" + pointName + "' неизвестный тип: " + e.getMessage(), e);
        }
    }

    private static String requireText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw new MemoryFormatException("У точки памяти поле '" + field + "' обязательно и должно быть строкой");
        }
        return value.textValue();
    }

    private static int requireInt(JsonNode node, String field, String pointName) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new MemoryFormatException("У точки памяти '" + pointName + "' поле '" + field
                    + "' обязательно и должно быть целым числом в диапазоне int");
        }
        return value.intValue();
    }
}
