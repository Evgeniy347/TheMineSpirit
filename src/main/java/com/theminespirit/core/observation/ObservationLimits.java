package com.theminespirit.core.observation;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.reload.DataTables;
import java.util.Set;

/**
 * Пределы размера наблюдения: сколько выпавших предметов, сколько контейнеров и сколько записей
 * в каждом контейнере попадает в ответ {@code observe}. Числа — данные: правятся через /reload.
 */
public record ObservationLimits(int maxGroundItems, int maxContainers, int maxContainerItems, int maxOpenBlocks) {
    public static final String RESOURCE = "data/theminespirit/observation/observation.json";

    private static final Set<String> FIELDS =
            Set.of("maxGroundItems", "maxContainers", "maxContainerItems", "maxOpenBlocks");
    private static final int MAX_PER_FIELD = 512;
    private static final ObservationLimits DEFAULTS = new ObservationLimits(16, 8, 27, 24);

    public ObservationLimits {
        requirePositive("maxGroundItems", maxGroundItems);
        requirePositive("maxContainers", maxContainers);
        requirePositive("maxContainerItems", maxContainerItems);
        requirePositive("maxOpenBlocks", maxOpenBlocks);
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
                    JsonResource.integer(root.get("maxGroundItems"), origin, "maxGroundItems"),
                    JsonResource.integer(root.get("maxContainers"), origin, "maxContainers"),
                    JsonResource.integer(root.get("maxContainerItems"), origin, "maxContainerItems"),
                    JsonResource.integer(root.get("maxOpenBlocks"), origin, "maxOpenBlocks"));
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл '" + origin + "': " + error.getMessage(), error);
        }
    }
}
