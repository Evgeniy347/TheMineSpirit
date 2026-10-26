package com.theminespirit.core.board;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import java.util.Set;

public record WoodRules(int stockPlanks) {
    public static final String RESOURCE = "data/theminespirit/board/wood.json";
    private static final Set<String> FIELDS = Set.of("stockPlanks");

    public WoodRules {
        if (stockPlanks < 1) {
            throw new IllegalArgumentException("Запас для выбора породы " + stockPlanks
                    + " досок: нужен хотя бы 1, иначе порода постройки решается пустым запасом");
        }
    }

    public static WoodRules parse(String json, String origin) {
        JsonNode root = JsonResource.parse(json, origin);
        JsonResource.checkFields(root, FIELDS, origin, "wood.json");
        try {
            return new WoodRules(JsonResource.integer(root.get("stockPlanks"), origin, "stockPlanks"));
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл '" + origin + "': " + error.getMessage(), error);
        }
    }

    public static WoodRules load() {
        return parse(JsonResource.readClasspath(RESOURCE), RESOURCE);
    }
}
