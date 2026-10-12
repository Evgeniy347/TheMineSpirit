package com.theminespirit.core.planner;

import java.util.List;

public interface ResourceKnowledge {
    boolean isGatherable(String item);

    boolean isCraftable(String item);

    int outputCount(String item);

    List<Ingredient> ingredientsFor(String item);

    boolean isSmeltable(String item);

    String smeltInputFor(String item);

    default int burnTicks(String item) {
        return FuelTable.burnTicks(item);
    }

    default List<String> fuelCandidates() {
        return FuelTable.preferenceOrder();
    }
}