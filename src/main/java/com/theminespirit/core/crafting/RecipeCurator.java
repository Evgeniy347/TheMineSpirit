package com.theminespirit.core.crafting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class RecipeCurator {
    private RecipeCurator() {}

    public static List<RecipeSpec> curate(List<RecipeSpec> recipes) {
        Objects.requireNonNull(recipes, "recipes не может быть null: куратору нечего отбирать");
        Set<String> smelted = smeltedProducts(recipes);
        List<RecipeSpec> kept = new ArrayList<>(recipes.size());
        for (RecipeSpec recipe : recipes) {
            if (recipe.kind() == RecipeKind.SMELTING
                    || !smelted.contains(recipe.outputItem()) && !consumesOwnResult(recipe)) {
                kept.add(recipe);
            }
        }
        Map<String, List<RecipeSpec>> craftsByOutput = craftsByOutput(kept);
        List<RecipeSpec> curated = new ArrayList<>(kept.size());
        for (RecipeSpec recipe : kept) {
            if (recipe.kind() == RecipeKind.SMELTING || !isDecompression(recipe, craftsByOutput)) {
                curated.add(recipe);
            }
        }
        return List.copyOf(curated);
    }

    private static Set<String> smeltedProducts(List<RecipeSpec> recipes) {
        Set<String> products = new HashSet<>();
        for (RecipeSpec recipe : recipes) {
            if (recipe.kind() == RecipeKind.SMELTING) {
                products.add(recipe.outputItem());
            }
        }
        return products;
    }

    private static boolean consumesOwnResult(RecipeSpec recipe) {
        return consumes(recipe, recipe.outputItem());
    }

    private static boolean consumes(RecipeSpec recipe, String item) {
        for (IngredientSpec ingredient : recipe.ingredients()) {
            if (ingredient.alternatives().contains(item)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, List<RecipeSpec>> craftsByOutput(List<RecipeSpec> recipes) {
        Map<String, List<RecipeSpec>> byOutput = new HashMap<>();
        for (RecipeSpec recipe : recipes) {
            if (recipe.kind() != RecipeKind.SMELTING) {
                byOutput.computeIfAbsent(recipe.outputItem(), key -> new ArrayList<>())
                        .add(recipe);
            }
        }
        return byOutput;
    }

    private static boolean isDecompression(RecipeSpec recipe, Map<String, List<RecipeSpec>> craftsByOutput) {
        if (recipe.outputCount() <= recipe.ingredients().size()) {
            return false;
        }
        for (IngredientSpec ingredient : recipe.ingredients()) {
            for (String source : ingredient.alternatives()) {
                for (RecipeSpec candidate : craftsByOutput.getOrDefault(source, List.of())) {
                    if (candidate != recipe && consumes(candidate, recipe.outputItem())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
