package com.theminespirit.core.crafting;

import java.util.Optional;

public final class Stations {
    public static final String CRAFTING_TABLE = "crafting_table";
    public static final String FURNACE = "furnace";

    private Stations() {
    }

    public static Optional<String> forRecipe(RecipeSpec recipe) {
        if (recipe.kind() == RecipeKind.SMELTING) {
            return Optional.of(FURNACE);
        }
        return recipe.needsTable() ? Optional.of(CRAFTING_TABLE) : Optional.empty();
    }
}
