package com.theminespirit.core.planner;

import com.theminespirit.core.script.CatalogLimits;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public interface ResourceKnowledge {
    record SourceScript(String script, Map<String, Object> params, int maxCount) {
        public SourceScript {
            if (script == null || script.isBlank()) {
                throw new IllegalArgumentException("Источник без скрипта: дух не знает, чем добывать предмет");
            }
            Objects.requireNonNull(params, "Параметры источника не могут быть null: пустые задают пустой картой");
            if (maxCount < 1 || maxCount > CatalogLimits.COUNT_MAX) {
                throw new IllegalArgumentException("Предел источника " + maxCount + " вне [1, "
                        + CatalogLimits.COUNT_MAX + "]: порция добычи не может быть больше стака");
            }
            params = Map.copyOf(params);
        }

        public SourceScript(String script, Map<String, Object> params) {
            this(script, params, CatalogLimits.COUNT_MAX);
        }
    }

    boolean isGatherable(String item);

    default Optional<SourceScript> sourceScript(String item) {
        return Optional.empty();
    }

    default boolean isCraftedProduct(String item) {
        return CraftedItems.isCrafted(item);
    }

    default boolean isBrushed(String item) {
        return false;
    }

    default String gatherBlockFor(String item) {
        return item;
    }

    boolean isCraftable(String item);

    int outputCount(String item);

    List<Ingredient> ingredientsFor(String item);

    boolean isSmeltable(String item);

    String smeltInputFor(String item);

    default Optional<String> stationFor(String item) {
        return Optional.empty();
    }

    default Optional<String> toolForBlock(String block) {
        return Optional.empty();
    }

    default int burnTicks(String item) {
        return FuelTable.burnTicks(item);
    }

    default List<String> fuelCandidates() {
        return FuelTable.preferenceOrder();
    }
}
