package com.theminespirit.core.board.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record StoreMinimums(Map<String, Entry> items) {
    public static final String RESOURCE = "data/theminespirit/board/store_minimum.json";
    private static final Set<String> ROOT_FIELDS = Set.of("items");
    private static final Set<String> ENTRY_FIELDS = Set.of("minimum", "reserve");

    public record Entry(int minimum, int reserve) {
        public Entry {
            if (minimum < 0 || reserve < 0) {
                throw new IllegalArgumentException("Минимум склада " + minimum + " и резерв " + reserve
                        + " не бывают отрицательными: отсутствие задают нулём");
            }
        }

        public int target() {
            return minimum + reserve;
        }
    }

    public StoreMinimums {
        items = Map.copyOf(Objects.requireNonNull(items, "Минимумы склада не могут быть null"));
    }

    public static StoreMinimums loadDefault() {
        return load(RESOURCE);
    }

    public static StoreMinimums load(String resourcePath) {
        return parse(JsonResource.readClasspath(resourcePath), resourcePath);
    }

    public static StoreMinimums parse(String json, String origin) {
        JsonNode root = JsonResource.parse(json, origin);
        JsonResource.checkFields(root, ROOT_FIELDS, origin, "файла минимумов склада");
        JsonNode node = root.get("items");
        if (node == null || !node.isObject() || node.isEmpty()) {
            throw new IllegalStateException("Файл '" + origin + "' без items: склад без минимумов нечего держать");
        }
        Map<String, Entry> items = new LinkedHashMap<>();
        node.properties().forEach(entry -> {
            String key = entry.getKey();
            if (key.isBlank()) {
                throw new IllegalStateException("Файл '" + origin + "': пустое имя предмета склада");
            }
            JsonResource.checkFields(entry.getValue(), ENTRY_FIELDS, origin, "предмета склада '" + key + "'");
            items.put(
                    key,
                    new Entry(
                            JsonResource.integer(
                                    entry.getValue().get("minimum"), origin, "поля minimum предмета '" + key + "'"),
                            JsonResource.integer(
                                    entry.getValue().get("reserve"), origin, "поля reserve предмета '" + key + "'")));
        });
        return new StoreMinimums(items);
    }
}
