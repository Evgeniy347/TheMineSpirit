package com.theminespirit.core.observation;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.reload.DataTables;
import java.util.Set;

public record ObservationLimits(int maxGroundItems, int maxContainers, int maxContainerItems, int maxOpenBlocks) {
    public static final String RESOURCE = "data/theminespirit/observation/observation.json";

    private static final String MAX_GROUND_ITEMS = "maxGroundItems";
    private static final String MAX_CONTAINERS = "maxContainers";
    private static final String MAX_CONTAINER_ITEMS = "maxContainerItems";
    private static final String MAX_OPEN_BLOCKS = "maxOpenBlocks";
    private static final Set<String> FIELDS =
            Set.of(MAX_GROUND_ITEMS, MAX_CONTAINERS, MAX_CONTAINER_ITEMS, MAX_OPEN_BLOCKS);
    private static final int MAX_PER_FIELD = 512;
    private static final ObservationLimits DEFAULTS = new ObservationLimits(16, 8, 27, 24);

    public ObservationLimits {
        requirePositive(MAX_GROUND_ITEMS, maxGroundItems);
        requirePositive(MAX_CONTAINERS, maxContainers);
        requirePositive(MAX_CONTAINER_ITEMS, maxContainerItems);
        requirePositive(MAX_OPEN_BLOCKS, maxOpenBlocks);
    }

    private static void requirePositive(String field, int value) {
        if (value < 1 || value > MAX_PER_FIELD) {
            throw new IllegalArgumentException(field + " = " + value + " вне [1, " + MAX_PER_FIELD
                    + "]: предел наблюдения не положителен или настолько велик, что ответ раздуется");
        }
    }

    public static ObservationLimits defaults() {
        return DEFAULTS;
    }

    public static ObservationLimits loadDefault() {
        return DataTables.INSTANCE.observation().current();
    }

    public static ObservationLimits load() {
        return parse(JsonResource.readClasspath(RESOURCE), RESOURCE);
    }

    public static ObservationLimits parse(String json, String origin) {
        JsonNode root = JsonResource.parse(json, origin);
        JsonResource.checkFields(root, FIELDS, origin, "observation.json");
        try {
            return new ObservationLimits(
                    JsonResource.integer(root.get(MAX_GROUND_ITEMS), origin, MAX_GROUND_ITEMS),
                    JsonResource.integer(root.get(MAX_CONTAINERS), origin, MAX_CONTAINERS),
                    JsonResource.integer(root.get(MAX_CONTAINER_ITEMS), origin, MAX_CONTAINER_ITEMS),
                    JsonResource.integer(root.get(MAX_OPEN_BLOCKS), origin, MAX_OPEN_BLOCKS));
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл '" + origin + "': " + error.getMessage(), error);
        }
    }
}
