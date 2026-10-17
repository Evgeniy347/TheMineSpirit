package com.theminespirit.core.repair;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.reload.DataTables;
import java.util.Set;

public record RepairData(int maxDurabilityPercent) {
    public static final String RESOURCE = "data/theminespirit/repair/repair.json";

    public static final int PERCENT_MIN = 1;
    public static final int PERCENT_MAX = 100;
    public static final int PERCENT_DEFAULT = 90;

    private static final Set<String> FIELDS = Set.of("maxDurabilityPercent");
    private static final RepairData DEFAULTS = new RepairData(PERCENT_DEFAULT);

    public RepairData {
        if (maxDurabilityPercent < PERCENT_MIN || maxDurabilityPercent > PERCENT_MAX) {
            throw new IllegalArgumentException("Порог прочности " + maxDurabilityPercent + "% вне диапазона ["
                    + PERCENT_MIN + ", " + PERCENT_MAX + "]: порог — доля максимальной прочности, выше 100% "
                    + "не чинилось бы ничего, а ноль чинил бы любое");
        }
    }

    public static RepairData defaults() {
        return DEFAULTS;
    }

    public static RepairData loadDefault() {
        return DataTables.INSTANCE.repair().current();
    }

    public static RepairData load() {
        return parse(JsonResource.readClasspath(RESOURCE), RESOURCE);
    }

    public static RepairData parse(String json, String origin) {
        JsonNode root = JsonResource.parse(json, origin);
        JsonResource.checkFields(root, FIELDS, origin, "repair.json");
        int percent = JsonResource.integer(root.get("maxDurabilityPercent"), origin, "maxDurabilityPercent");
        try {
            return new RepairData(percent);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл '" + origin + "': " + error.getMessage(), error);
        }
    }
}
