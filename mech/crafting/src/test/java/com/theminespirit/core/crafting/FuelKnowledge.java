package com.theminespirit.core.crafting;

import com.theminespirit.core.planner.Ingredient;
import com.theminespirit.core.planner.ResourceKnowledge;
import java.util.List;

final class FuelKnowledge implements ResourceKnowledge {
    @Override
    public boolean isGatherable(String item) {
        return true;
    }

    @Override
    public boolean isCraftable(String item) {
        return false;
    }

    @Override
    public int outputCount(String item) {
        return 1;
    }

    @Override
    public List<Ingredient> ingredientsFor(String item) {
        return List.of();
    }

    @Override
    public boolean isSmeltable(String item) {
        return false;
    }

    @Override
    public String smeltInputFor(String item) {
        return null;
    }
}
