package com.theminespirit.core.fishing;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.reload.DataTables;
import java.util.Set;

public record FishingData(int minWidth, int minDepth, int workWidth, int workDepth, int bucketCount, int smokerFish) {
    public static final String RESOURCE = "data/theminespirit/fishing/fishing.json";

    public static final int MIN_WIDTH_MIN = 2;
    public static final int MIN_WIDTH_MAX = 8;
    public static final int MIN_DEPTH_MIN = 1;
    public static final int MIN_DEPTH_MAX = 8;

    public static final int WORK_WIDTH_DEFAULT = 5;
    public static final int WORK_DEPTH_DEFAULT = 2;
    public static final int BUCKET_COUNT_MIN = 1;
    public static final int BUCKET_COUNT_MAX = 64;
    public static final int BUCKET_COUNT_DEFAULT = 8;
    public static final int SMOKER_FISH_MIN = 1;
    public static final int SMOKER_FISH_MAX = 256;
    public static final int SMOKER_FISH_DEFAULT = 16;

    private static final Set<String> FIELDS =
            Set.of("minWidth", "minDepth", "workWidth", "workDepth", "bucketCount", "smokerFish");
    private static final FishingData DEFAULTS =
            new FishingData(2, 1, WORK_WIDTH_DEFAULT, WORK_DEPTH_DEFAULT, BUCKET_COUNT_DEFAULT, SMOKER_FISH_DEFAULT);

    public FishingData(int minWidth, int minDepth) {
        this(minWidth, minDepth, WORK_WIDTH_DEFAULT, WORK_DEPTH_DEFAULT, BUCKET_COUNT_DEFAULT, SMOKER_FISH_DEFAULT);
    }

    public FishingData {
        checkWater(
                "Сторона открытой воды",
                minWidth,
                MIN_WIDTH_MIN,
                MIN_WIDTH_MAX,
                "уже 2x2 дух не забросит удочку, шире 8x8 рыбалка ищет море вместо пруда");
        checkWater(
                "Глубина открытой воды",
                minDepth,
                MIN_DEPTH_MIN,
                MIN_DEPTH_MAX,
                "на мели нет рыбы, а глубже 8 блоков вода уже не пруд");
        checkWater(
                "Сторона рабочей точки",
                workWidth,
                MIN_WIDTH_MIN,
                MIN_WIDTH_MAX,
                "рабочая точка ставится у открытой воды того же порядка, что и разовая ловля");
        checkWater(
                "Глубина рабочей точки",
                workDepth,
                MIN_DEPTH_MIN,
                MIN_DEPTH_MAX,
                "рабочая точка ставится у открытой воды того же порядка, что и разовая ловля");
        checkCount(
                "Предел рыбы в вёдрах",
                bucketCount,
                BUCKET_COUNT_MIN,
                BUCKET_COUNT_MAX,
                "за один заход ведром ловят 1..64 рыбы, иначе запас ведёрной рыбы неограничен");
        checkCount(
                "Порог рыбы для коптильни",
                smokerFish,
                SMOKER_FISH_MIN,
                SMOKER_FISH_MAX,
                "коптильню крафтят, когда сырой рыбы набралось на этот порог");
    }

    public static FishingData defaults() {
        return DEFAULTS;
    }

    public static FishingData loadDefault() {
        return DataTables.INSTANCE.fishing().current();
    }

    public static FishingData load() {
        return parse(JsonResource.readClasspath(RESOURCE), RESOURCE);
    }

    public static FishingData parse(String json, String origin) {
        JsonNode root = JsonResource.parse(json, origin);
        JsonResource.checkFields(root, FIELDS, origin, "fishing.json");
        int minWidth = JsonResource.integer(root.get("minWidth"), origin, "minWidth");
        int minDepth = JsonResource.integer(root.get("minDepth"), origin, "minDepth");
        int workWidth = optional(root, origin, "workWidth", WORK_WIDTH_DEFAULT);
        int workDepth = optional(root, origin, "workDepth", WORK_DEPTH_DEFAULT);
        int bucketCount = optional(root, origin, "bucketCount", BUCKET_COUNT_DEFAULT);
        int smokerFish = optional(root, origin, "smokerFish", SMOKER_FISH_DEFAULT);
        try {
            return new FishingData(minWidth, minDepth, workWidth, workDepth, bucketCount, smokerFish);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл '" + origin + "': " + error.getMessage(), error);
        }
    }

    private static int optional(JsonNode root, String origin, String field, int fallback) {
        return root.has(field) ? JsonResource.integer(root.get(field), origin, field) : fallback;
    }

    private static void checkWater(String name, int value, int min, int max, String why) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    name + " " + value + " вне диапазона [" + min + ", " + max + "]: " + why);
        }
    }

    private static void checkCount(String name, int value, int min, int max, String why) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    name + " " + value + " вне диапазона [" + min + ", " + max + "]: " + why);
        }
    }
}
