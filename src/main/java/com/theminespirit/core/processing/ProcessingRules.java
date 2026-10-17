package com.theminespirit.core.processing;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.intellect.IntellectTuning;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.script.CatalogLimits;
import java.util.Set;

public record ProcessingRules(
        int charcoalTarget,
        int plankTarget,
        int stickTarget,
        int charcoalCount,
        int cookCount,
        int craftCount,
        double baseDemand,
        double demandScale,
        int stationRadius) {

    public static final String RESOURCE = "data/theminespirit/processing/processing.json";

    private static final Set<String> FIELDS = Set.of("charcoalTarget", "plankTarget", "stickTarget",
            "charcoalCount", "cookCount", "craftCount", "baseDemand", "demandScale", "stationRadius");

    private static final ProcessingRules DEFAULTS =
            new ProcessingRules(16, 32, 32, 8, 4, 8, 1.0, 8.0, CatalogLimits.STATION_RADIUS_DEFAULT);

    public ProcessingRules {
        requireTarget("charcoalTarget", charcoalTarget);
        requireTarget("plankTarget", plankTarget);
        requireTarget("stickTarget", stickTarget);
        requireCount("charcoalCount", charcoalCount);
        requireCount("cookCount", cookCount);
        requireCount("craftCount", craftCount);
        if (Double.isNaN(baseDemand) || Double.isInfinite(baseDemand) || baseDemand < 0
                || baseDemand > IntellectTuning.MAX_DEMAND) {
            throw new IllegalArgumentException("Базовый спрос переработки " + baseDemand
                    + ": обязан быть конечным числом в 0.." + IntellectTuning.MAX_DEMAND
                    + ", иначе распределение сравнивает несравнимое");
        }
        if (Double.isNaN(demandScale) || Double.isInfinite(demandScale) || demandScale < 0) {
            throw new IllegalArgumentException("Множитель спроса переработки " + demandScale
                    + ": обязан быть конечным и не меньше 0, иначе спрос не привязан к дефициту");
        }
        if (stationRadius < CatalogLimits.STATION_RADIUS_MIN || stationRadius > CatalogLimits.STATION_RADIUS_MAX) {
            throw new IllegalArgumentException("Радиус станции переработки " + stationRadius + " вне ["
                    + CatalogLimits.STATION_RADIUS_MIN + ", " + CatalogLimits.STATION_RADIUS_MAX
                    + "]: по нему ищется печь/коптильня/верстак");
        }
    }

    public static ProcessingRules defaults() {
        return DEFAULTS;
    }

    public static ProcessingRules loadDefault() {
        return DataTables.INSTANCE.processing().current();
    }

    public static ProcessingRules load() {
        return parse(JsonResource.readClasspath(RESOURCE), RESOURCE);
    }

    public static ProcessingRules parse(String json, String origin) {
        JsonNode root = JsonResource.parse(json, origin);
        JsonResource.checkFields(root, FIELDS, origin, "processing.json");
        try {
            return new ProcessingRules(
                    JsonResource.integer(root.get("charcoalTarget"), origin, "charcoalTarget"),
                    JsonResource.integer(root.get("plankTarget"), origin, "plankTarget"),
                    JsonResource.integer(root.get("stickTarget"), origin, "stickTarget"),
                    JsonResource.integer(root.get("charcoalCount"), origin, "charcoalCount"),
                    JsonResource.integer(root.get("cookCount"), origin, "cookCount"),
                    JsonResource.integer(root.get("craftCount"), origin, "craftCount"),
                    JsonResource.decimal(root.get("baseDemand"), origin, "baseDemand"),
                    JsonResource.decimal(root.get("demandScale"), origin, "demandScale"),
                    JsonResource.integer(root.get("stationRadius"), origin, "stationRadius"));
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл '" + origin + "': " + error.getMessage(), error);
        }
    }

    private static void requireTarget(String name, int target) {
        if (target < 0) {
            throw new IllegalArgumentException("Цель запаса " + name + " = " + target
                    + ": норма не может быть отрицательной, иначе дефицит отрицателен");
        }
    }

    private static void requireCount(String name, int count) {
        if (count < CatalogLimits.COUNT_MIN || count > CatalogLimits.COUNT_MAX) {
            throw new IllegalArgumentException("Число предметов за запуск " + name + " = " + count
                    + " вне диапазона [" + CatalogLimits.COUNT_MIN + ", " + CatalogLimits.COUNT_MAX
                    + "]: скрипт отвергнет план с таким count");
        }
    }
}
