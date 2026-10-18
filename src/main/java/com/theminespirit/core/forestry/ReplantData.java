package com.theminespirit.core.forestry;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.reload.DataTables;
import java.util.Set;

public record ReplantData(int squareSaplings, int areaRadius, int areaStep, int buildingGap) {
    public static final String RESOURCE = "data/theminespirit/forestry/replant.json";

    public static final int SQUARE_SAPLINGS_MIN = 4;
    public static final int SQUARE_SAPLINGS_MAX = 9;
    public static final int AREA_RADIUS_MIN = 1;
    public static final int AREA_RADIUS_MAX = 32;
    public static final int AREA_STEP_MIN = 1;
    public static final int AREA_STEP_MAX = 8;
    public static final int BUILDING_GAP_MIN = 1;
    public static final int BUILDING_GAP_MAX = 8;

    private static final Set<String> FIELDS = Set.of("squareSaplings", "areaRadius", "areaStep", "buildingGap");
    private static final ReplantData DEFAULTS = new ReplantData(4, 6, 3, 2);

    public ReplantData {
        if (squareSaplings < SQUARE_SAPLINGS_MIN || squareSaplings > SQUARE_SAPLINGS_MAX) {
            throw new IllegalArgumentException("Саженцев на квадрат " + squareSaplings + " вне диапазона ["
                    + SQUARE_SAPLINGS_MIN + ", " + SQUARE_SAPLINGS_MAX
                    + "]: квадрат 2x2 требует ровно четыре саженца, больше девяти на место ствола не влезет");
        }
        if (areaRadius < AREA_RADIUS_MIN || areaRadius > AREA_RADIUS_MAX) {
            throw new IllegalArgumentException("Радиус обновления участка " + areaRadius + " вне диапазона ["
                    + AREA_RADIUS_MIN + ", " + AREA_RADIUS_MAX
                    + "]: нулевой радиус не досадит ни одного саженца, радиус шире 32 захватит чужие постройки");
        }
        if (areaStep < AREA_STEP_MIN || areaStep > AREA_STEP_MAX) {
            throw new IllegalArgumentException("Шаг досадки " + areaStep + " вне диапазона ["
                    + AREA_STEP_MIN + ", " + AREA_STEP_MAX
                    + "]: нулевой шаг повторяет одну клетку, слишком широкий оставляет лес редким");
        }
        if (buildingGap < BUILDING_GAP_MIN || buildingGap > BUILDING_GAP_MAX) {
            throw new IllegalArgumentException("Зазор до построек " + buildingGap + " вне диапазона ["
                    + BUILDING_GAP_MIN + ", " + BUILDING_GAP_MAX
                    + "]: нулевой зазор ставит саженец вплотную к стене игрока");
        }
    }

    public static ReplantData defaults() {
        return DEFAULTS;
    }

    public static ReplantData loadDefault() {
        return DataTables.INSTANCE.replant().current();
    }

    public static ReplantData load() {
        return parse(JsonResource.readClasspath(RESOURCE), RESOURCE);
    }

    public static ReplantData parse(String json, String origin) {
        JsonNode root = JsonResource.parse(json, origin);
        JsonResource.checkFields(root, FIELDS, origin, "replant.json");
        int squareSaplings = JsonResource.integer(root.get("squareSaplings"), origin, "squareSaplings");
        int areaRadius = JsonResource.integer(root.get("areaRadius"), origin, "areaRadius");
        int areaStep = JsonResource.integer(root.get("areaStep"), origin, "areaStep");
        int buildingGap = JsonResource.integer(root.get("buildingGap"), origin, "buildingGap");
        try {
            return new ReplantData(squareSaplings, areaRadius, areaStep, buildingGap);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл '" + origin + "': " + error.getMessage(), error);
        }
    }
}
