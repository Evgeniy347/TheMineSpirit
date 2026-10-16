package com.theminespirit.core.fishing;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import java.util.Set;

public record FishingData(int minWidth, int minDepth) {
    public static final String RESOURCE = "data/theminespirit/fishing/fishing.json";

    public static final int MIN_WIDTH_MIN = 2;
    public static final int MIN_WIDTH_MAX = 8;
    public static final int MIN_DEPTH_MIN = 1;
    public static final int MIN_DEPTH_MAX = 8;

    private static final Set<String> FIELDS = Set.of("minWidth", "minDepth");
    private static final FishingData DEFAULTS = new FishingData(2, 1);

    public FishingData {
        if (minWidth < MIN_WIDTH_MIN || minWidth > MIN_WIDTH_MAX) {
            throw new IllegalArgumentException("Сторона открытой воды " + minWidth + " вне диапазона ["
                    + MIN_WIDTH_MIN + ", " + MIN_WIDTH_MAX
                    + "]: уже 2x2 дух не забросит удочку, шире 8x8 рыбалка ищет море вместо пруда");
        }
        if (minDepth < MIN_DEPTH_MIN || minDepth > MIN_DEPTH_MAX) {
            throw new IllegalArgumentException("Глубина открытой воды " + minDepth + " вне диапазона ["
                    + MIN_DEPTH_MIN + ", " + MIN_DEPTH_MAX
                    + "]: на мели нет рыбы, а глубже 8 блоков вода уже не пруд");
        }
    }

    public static FishingData defaults() {
        return DEFAULTS;
    }

    public static FishingData load() {
        return parse(JsonResource.readClasspath(RESOURCE), RESOURCE);
    }

    public static FishingData parse(String json, String origin) {
        JsonNode root = JsonResource.parse(json, origin);
        JsonResource.checkFields(root, FIELDS, origin, "fishing.json");
        int minWidth = JsonResource.integer(root.get("minWidth"), origin, "minWidth");
        int minDepth = JsonResource.integer(root.get("minDepth"), origin, "minDepth");
        try {
            return new FishingData(minWidth, minDepth);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл '" + origin + "': " + error.getMessage(), error);
        }
    }
}
