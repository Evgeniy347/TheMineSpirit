package com.theminespirit.core.husbandry;

import com.theminespirit.core.reload.DataTables;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.inventory.JsonResource;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public enum SwordTier {
    HAND,
    WOOD,
    STONE,
    GOLD,
    IRON,
    DIAMOND,
    NETHERITE;

    public static final String RESOURCE = "data/theminespirit/husbandry/sword.json";
    public static final String EMPTY_HAND_ID = "minecraft:air";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public boolean atLeast(SwordTier other) {
        Objects.requireNonNull(other, "Порог не может быть null: без него не с чем сравнивать тир");
        return compareTo(other) >= 0;
    }

    public int damage() {
        return switch (this) {
            case HAND -> 1;
            case WOOD -> 4;
            case STONE -> 5;
            case GOLD -> 4;
            case IRON -> 6;
            case DIAMOND -> 7;
            case NETHERITE -> 8;
        };
    }

    public static SwordTier ofItem(String itemId) {
        Objects.requireNonNull(itemId, "id предмета не может быть null: без него тир меча не определить");
        SwordTier tier = items().get(canonicalId(itemId));
        if (tier == null) {
            throw new IllegalArgumentException("Предмет '" + itemId + "' — не меч из таблицы " + RESOURCE
                    + ": бить им стадо запрещено, возьмите меч из таблицы");
        }
        return tier;
    }

    static Map<String, SwordTier> load(Map<String, String> raw) {
        Objects.requireNonNull(raw, "Данные таблицы мечей не могут быть null: разбирать нечего");
        Map<String, SwordTier> table = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            SwordTier tier;
            try {
                tier = valueOf(entry.getValue().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException error) {
                throw new IllegalStateException("Таблица мечей: неизвестный тир '" + entry.getValue()
                        + "' у предмета '" + entry.getKey() + "': допустимы wood, stone, gold, iron, diamond, "
                        + "netherite, исправьте файл " + RESOURCE);
            }
            if (tier == HAND) {
                throw new IllegalStateException("Таблица мечей: руки в таблице не описываются, пустая рука всегда "
                        + "ниже дерева, исправьте файл " + RESOURCE);
            }
            table.put(canonicalId(entry.getKey()), tier);
        }
        table.put(EMPTY_HAND_ID, HAND);
        return Map.copyOf(table);
    }

    public static Map<String, SwordTier> items() {
        return DataTables.INSTANCE.swords().current();
    }

    public static Map<String, SwordTier> parse(String json, String origin) {
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Таблица '" + origin + "' — не разобран JSON: "
                    + error.getOriginalMessage() + ". Исправьте синтаксис файла");
        }
        if (root == null || !root.isObject()) {
            throw new IllegalStateException("Таблица '" + origin + "' — не объект: ожидается объект "
                    + "«id предмета → тир»");
        }
        Map<String, String> raw = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            raw.put(field.getKey(), JsonResource.text(field.getValue(), origin, "предмета '" + field.getKey() + "'"));
        }
        return load(raw);
    }

    public static Map<String, SwordTier> loadFile() {
        return parse(JsonResource.readClasspath(RESOURCE), RESOURCE);
    }

    private static String canonicalId(String item) {
        Objects.requireNonNull(item, "id предмета не может быть null: по нему ищется тир меча");
        return item.indexOf(':') < 0 ? "minecraft:" + item : item;
    }
}
