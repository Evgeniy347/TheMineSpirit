package com.theminespirit.core.crafting;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public final class Stations {
    public static final String CRAFTING_TABLE = "crafting_table";
    public static final String FURNACE = "furnace";
    public static final String SMOKER = "smoker";
    public static final String CAMPFIRE = "campfire";

    private Stations() {
    }

    public static Optional<String> forRecipe(RecipeSpec recipe) {
        if (recipe.kind() == RecipeKind.SMELTING) {
            return Optional.of(FURNACE);
        }
        return recipe.needsTable() ? Optional.of(CRAFTING_TABLE) : Optional.empty();
    }

    public static boolean isFoodProduct(String product) {
        if (product == null) {
            return false;
        }
        String normalized = product.toLowerCase();
        return normalized.startsWith("cooked_") || normalized.equals("baked_potato")
                || normalized.equals("cooked_beef") || normalized.equals("cooked_porkchop")
                || normalized.equals("cooked_chicken") || normalized.equals("cooked_mutton")
                || normalized.equals("cooked_rabbit") || normalized.equals("cooked_cod")
                || normalized.equals("cooked_salmon");
    }

    public static Optional<String> preferredSmeltingStation(String product, Collection<String> available) {
        Set<String> set = available == null ? Set.of() : new HashSet<>(available);
        boolean food = isFoodProduct(product);
        if (food) {
            if (set.contains(SMOKER)) {
                return Optional.of(SMOKER);
            }
            if (set.contains(FURNACE)) {
                return Optional.of(FURNACE);
            }
            if (set.contains(CAMPFIRE)) {
                return Optional.of(CAMPFIRE);
            }
            return Optional.empty();
        }
        if (set.contains(FURNACE)) {
            return Optional.of(FURNACE);
        }
        if (set.contains(SMOKER)) {
            return Optional.of(SMOKER);
        }
        if (set.contains(CAMPFIRE)) {
            return Optional.of(CAMPFIRE);
        }
        return Optional.empty();
    }
}
