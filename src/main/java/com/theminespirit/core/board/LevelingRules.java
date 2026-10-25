package com.theminespirit.core.board;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import java.util.Set;

public record LevelingRules(int surplusPercent, int batch, int chestKeep) {
    public static final String RESOURCE = "data/theminespirit/board/leveling.json";
    private static final Set<String> FIELDS = Set.of("surplusPercent", "batch", "chestKeep");

    public LevelingRules {
        if (surplusPercent < 0 || batch < 1 || chestKeep < 0) {
            throw new IllegalArgumentException("Выравнивание: запас " + surplusPercent + "%, партия " + batch
                    + ", неприкосновенный запас сундуков " + chestKeep
                    + ": запас и минимум не отрицательные, партия — хотя бы блок");
        }
    }

    public boolean enough(int cut, int fill) {
        return 100L * cut >= (100L + surplusPercent) * fill;
    }

    public static LevelingRules parse(String json, String origin) {
        JsonNode root = JsonResource.parse(json, origin);
        JsonResource.checkFields(root, FIELDS, origin, "leveling.json");
        try {
            return new LevelingRules(
                    JsonResource.integer(root.get("surplusPercent"), origin, "surplusPercent"),
                    JsonResource.integer(root.get("batch"), origin, "batch"),
                    JsonResource.integer(root.get("chestKeep"), origin, "chestKeep"));
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл '" + origin + "': " + error.getMessage(), error);
        }
    }

    public static LevelingRules load() {
        return parse(JsonResource.readClasspath(RESOURCE), RESOURCE);
    }
}
