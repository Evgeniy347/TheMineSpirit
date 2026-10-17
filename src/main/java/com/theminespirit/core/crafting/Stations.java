package com.theminespirit.core.crafting;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class Stations {
    public static final String CRAFTING_TABLE = "crafting_table";
    public static final String FURNACE = "furnace";
    public static final String SMOKER = "smoker";
    public static final String CAMPFIRE = "campfire";
    public static final int CAMPFIRE_SLOTS = 4;

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
        int colon = normalized.indexOf(':');
        if (colon >= 0) {
            normalized = normalized.substring(colon + 1);
        }
        return normalized.startsWith("cooked_") || normalized.equals("baked_potato");
    }

    public static List<String> smeltingCandidates(String product) {
        if (isFoodProduct(product)) {
            return List.of(SMOKER, FURNACE, CAMPFIRE);
        }
        return List.of(FURNACE);
    }

    public static Optional<String> preferredSmeltingStation(String product, Collection<String> available) {
        Set<String> set = available == null ? Set.of() : new HashSet<>(available);
        return smeltingCandidates(product).stream().filter(set::contains).findFirst();
    }
}
