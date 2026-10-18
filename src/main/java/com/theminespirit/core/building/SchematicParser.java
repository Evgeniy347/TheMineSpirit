package com.theminespirit.core.building;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.script.CatalogLimits;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SchematicParser {
    private static final Set<String> ROOT_FIELDS = Set.of("version", "size", "blocks");
    private static final Set<String> BLOCK_FIELDS = Set.of("pos", "block", "state");
    private static final int SCHEMATIC_VERSION = 1;

    private SchematicParser() {
    }

    public static Schematic parse(String json, String origin) {
        JsonNode root = JsonResource.parse(json, origin);
        JsonResource.checkFields(root, ROOT_FIELDS, origin, "схемы");
        int version = JsonResource.integer(root.get("version"), origin, "поля version схемы");
        if (version != SCHEMATIC_VERSION) {
            throw new IllegalStateException("Файл '" + origin + "': версия схемы " + version + ", ожидается "
                    + SCHEMATIC_VERSION + ": схема из другой версии формата ставится не так");
        }
        JsonNode size = root.get("size");
        if (size == null || !size.isArray() || size.size() != 3) {
            throw new IllegalStateException("Файл '" + origin + "': у поля size схемы ожидается список из 3 чисел: размер задаёт границы блоков");
        }
        int sx = intAt(size, 0, origin);
        int sy = intAt(size, 1, origin);
        int sz = intAt(size, 2, origin);
        if (sx < 1 || sy < 1 || sz < 1) {
            throw new IllegalStateException("Файл '" + origin + "': размер схемы " + sx + "×" + sy + "×" + sz
                    + ": стороны обязаны быть хотя бы 1");
        }
        if (sx > CatalogLimits.SCHEMATIC_SIZE_MAX || sy > CatalogLimits.SCHEMATIC_SIZE_MAX || sz > CatalogLimits.SCHEMATIC_SIZE_MAX) {
            throw new IllegalStateException("Файл '" + origin + "': размер схемы " + sx + "×" + sy + "×" + sz
                    + " больше предела " + CatalogLimits.SCHEMATIC_SIZE_MAX + ": большая схема стротится слишком долго");
        }
        List<Schematic.Entry> entries = new ArrayList<>();
        for (JsonNode node : JsonResource.array(root.get("blocks"), origin, "списка blocks схемы")) {
            if (!node.isObject()) {
                throw new IllegalStateException("Файл '" + origin + "': блок схемы обязан быть объектом с pos и block");
            }
            JsonResource.checkFields(node, BLOCK_FIELDS, origin, "блока схемы");
            JsonNode pos = node.get("pos");
            if (pos == null || !pos.isArray() || pos.size() != 3) {
                throw new IllegalStateException("Файл '" + origin + "': у блока схемы pos обязан быть списком из 3 чисел");
            }
            int dx = intAt(pos, 0, origin);
            int dy = intAt(pos, 1, origin);
            int dz = intAt(pos, 2, origin);
            if (dx < 0 || dy < 0 || dz < 0 || dx >= sx || dy >= sy || dz >= sz) {
                throw new IllegalStateException("Файл '" + origin + "': позиция [" + dx + "," + dy + "," + dz
                        + "] вне размера " + sx + "×" + sy + "×" + sz + ": блок вне схемы ставить некуда");
            }
            entries.add(new Schematic.Entry(dx, dy, dz, JsonResource.text(node.get("block"), origin, "поля block схемы"),
                    state(node.get("state"), origin, dx, dy, dz)));
        }
        if (entries.size() > CatalogLimits.SCHEMATIC_BLOCKS_MAX) {
            throw new IllegalStateException("Файл '" + origin + "': блоков " + entries.size() + ", а предел "
                    + CatalogLimits.SCHEMATIC_BLOCKS_MAX + ": большая схема стротится слишком долго");
        }
        return new Schematic(sx, sy, sz, entries);
    }

    private static Map<String, String> state(JsonNode node, String origin, int dx, int dy, int dz) {
        if (node == null || node.isNull()) {
            return Map.of();
        }
        if (!node.isObject()) {
            throw new IllegalStateException("Файл '" + origin + "': у поля state блока [" + dx + "," + dy + "," + dz
                    + "] ожидается объект свойств: свойства задаются парами имя-значение");
        }
        Map<String, String> properties = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (field.getKey().isBlank()) {
                throw new IllegalStateException("Файл '" + origin + "': у состояния блока [" + dx + "," + dy + "," + dz
                        + "] пустое имя свойства: по пустому имени свойство не найти");
            }
            JsonNode value = field.getValue();
            if (!value.isTextual() || value.asText().isBlank()) {
                throw new IllegalStateException("Файл '" + origin + "': у свойства '" + field.getKey()
                        + "' блока [" + dx + "," + dy + "," + dz + "] ожидается непустая строка: "
                        + "значения свойств блока задаются текстом");
            }
            properties.put(field.getKey(), value.asText());
        }
        return Map.copyOf(properties);
    }

    private static int intAt(JsonNode list, int index, String origin) {
        JsonNode node = list.get(index);
        if (node == null || !node.isInt()) {
            throw new IllegalStateException("Файл '" + origin + "': координата " + index + " обязана быть целым числом");
        }
        return node.asInt();
    }

    public static List<Schematic.Entry> entriesOf(Schematic schematic) {
        return schematic.blocks();
    }
}
