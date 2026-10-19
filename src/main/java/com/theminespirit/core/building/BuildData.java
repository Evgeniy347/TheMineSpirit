package com.theminespirit.core.building;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import java.util.Set;

/**
 * Числа стройки из горячей таблицы {@code building/build.json}: досягаемость духа и темп постановки
 * (решение владельца 18.10: «примерно 2 блока в секунду, взмах руки и звук обязательны»).
 */
public record BuildData(SpiritReach reach, int placeIntervalTicks) {
    public static final String RESOURCE = "data/theminespirit/building/build.json";
    private static final Set<String> FIELDS = Set.of("reachHorizontal", "reachUp", "reachDown", "placeIntervalTicks");

    public BuildData {
        if (placeIntervalTicks < 1) {
            throw new IllegalArgumentException(
                    "placeIntervalTicks = " + placeIntervalTicks + ": между постановками хотя бы 1 тик");
        }
    }

    public static BuildData parse(String json, String origin) {
        JsonNode root = JsonResource.parse(json, origin);
        JsonResource.checkFields(root, FIELDS, origin, "build.json");
        try {
            SpiritReach reach = new SpiritReach(
                    JsonResource.integer(root.get("reachHorizontal"), origin, "reachHorizontal"),
                    JsonResource.integer(root.get("reachUp"), origin, "reachUp"),
                    JsonResource.integer(root.get("reachDown"), origin, "reachDown"));
            return new BuildData(
                    reach, JsonResource.integer(root.get("placeIntervalTicks"), origin, "placeIntervalTicks"));
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл '" + origin + "': " + error.getMessage(), error);
        }
    }

    public static BuildData load() {
        return parse(JsonResource.readClasspath(RESOURCE), RESOURCE);
    }
}
