package com.theminespirit.core.mining;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import java.util.Set;

public record DescentData(int targetY, int torchInterval) {
    public static final String RESOURCE = "data/theminespirit/mining/descent.json";

    private static final Set<String> FIELDS = Set.of("targetY", "torchInterval");
    private static final DescentData DEFAULTS = new DescentData(-58, 4);

    public DescentData {
        if (targetY < DescentPlan.WORLD_MIN_Y || targetY > DescentPlan.WORLD_MAX_Y) {
            throw new IllegalArgumentException("Целевой этаж " + targetY + " в данных спуска вне мира ["
                    + DescentPlan.WORLD_MIN_Y + ", " + DescentPlan.WORLD_MAX_Y
                    + "]: под дном мира копать некуда, а выше потолка сверлиться некуда");
        }
        if (torchInterval < MiningTuning.DESCENT_TORCH_INTERVAL_MIN
                || torchInterval > MiningTuning.DESCENT_TORCH_INTERVAL_MAX) {
            throw new IllegalArgumentException("Интервал факелов " + torchInterval + " вне диапазона ["
                    + MiningTuning.DESCENT_TORCH_INTERVAL_MIN + ", " + MiningTuning.DESCENT_TORCH_INTERVAL_MAX
                    + "]: редкий интервал оставит шахту тёмной, частый разорит запас факелов");
        }
    }

    public static DescentData defaults() {
        return DEFAULTS;
    }

    public static DescentData load() {
        return parse(JsonResource.readClasspath(RESOURCE), RESOURCE);
    }

    public static DescentData parse(String json, String origin) {
        JsonNode root = JsonResource.parse(json, origin);
        JsonResource.checkFields(root, FIELDS, origin, "descent.json");
        int targetY = JsonResource.integer(root.get("targetY"), origin, "targetY");
        int torchInterval = JsonResource.integer(root.get("torchInterval"), origin, "torchInterval");
        try {
            return new DescentData(targetY, torchInterval);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл '" + origin + "': " + error.getMessage(), error);
        }
    }
}
