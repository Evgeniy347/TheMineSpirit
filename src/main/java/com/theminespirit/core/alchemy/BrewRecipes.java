package com.theminespirit.core.alchemy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class BrewRecipes {
    public static final String BOTTLE = "glass_bottle";
    public static final String BASE_INGREDIENT = "nether_wart";
    public static final String SPLASH_INGREDIENT = "gunpowder";
    public static final String FUEL = "blaze_powder";
    public static final String POTION_ITEM = "minecraft:potion";
    public static final String SPLASH_ITEM = "minecraft:splash_potion";
    public static final String AWKWARD = "minecraft:awkward";
    public static final String SPLASH_PREFIX = "splash_potion_";
    public static final String POTION_PREFIX = "potion_";
    public static final int BOTTLES_PER_BREW = 3;
    private static final String MINECRAFT = "minecraft:";
    private static final Map<String, String> EFFECTS = effects();

    private BrewRecipes() {}

    public record Step(String ingredient, String item, String potion) {
        public Step {
            if (ingredient == null || ingredient.isBlank()) {
                throw new IllegalArgumentException("Шаг варки без ингредиента: дух не знает, что класть в стойку");
            }
            if (item == null || potion == null) {
                throw new IllegalArgumentException("Шаг варки " + ingredient + " без результата: нечего ждать");
            }
        }
    }

    public static Optional<String> effect(String product) {
        Objects.requireNonNull(product, "Продукт варки не может быть null: по нему берётся рецепт");
        if (product.startsWith(SPLASH_PREFIX)) {
            return Optional.ofNullable(EFFECTS.get(product.substring(SPLASH_PREFIX.length())))
                    .map(ignored -> product.substring(SPLASH_PREFIX.length()));
        }
        if (product.startsWith(POTION_PREFIX)) {
            return Optional.ofNullable(EFFECTS.get(product.substring(POTION_PREFIX.length())))
                    .map(ignored -> product.substring(POTION_PREFIX.length()));
        }
        return Optional.empty();
    }

    public static boolean splashing(String product) {
        Objects.requireNonNull(product, "Продукт варки не может быть null: по нему выбирается предмет зелья");
        return product.startsWith(SPLASH_PREFIX);
    }

    public static Optional<List<Step>> steps(String product) {
        Optional<String> effect = effect(product);
        if (effect.isEmpty()) {
            return Optional.empty();
        }
        String name = effect.orElseThrow();
        String ingredient = EFFECTS.get(name);
        if (ingredient == null) {
            return Optional.empty();
        }
        List<Step> steps = new ArrayList<>();
        steps.add(new Step(BASE_INGREDIENT, POTION_ITEM, AWKWARD));
        steps.add(new Step(ingredient, POTION_ITEM, MINECRAFT + name));
        if (splashing(product)) {
            steps.add(new Step(SPLASH_INGREDIENT, SPLASH_ITEM, MINECRAFT + name));
        }
        return Optional.of(List.copyOf(steps));
    }

    public static int batches(int pots) {
        if (pots < 1) {
            throw new IllegalArgumentException("Зелий за варку " + pots + ": варить нечего, число должно быть ≥ 1");
        }
        return (pots + BOTTLES_PER_BREW - 1) / BOTTLES_PER_BREW;
    }

    public static int fuel(String product, int pots) {
        return steps(product).orElseThrow().size() * batches(pots);
    }

    public static Map<String, Integer> needs(String product, int pots) {
        List<Step> steps = steps(product)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Нет ванильного рецепта зелья '" + product + "': граф не должен просить неведомое зелье"));
        int batches = batches(pots);
        Map<String, Integer> needs = new LinkedHashMap<>();
        needs.put(BOTTLE, pots);
        for (Step step : steps) {
            needs.merge(step.ingredient(), batches, Integer::sum);
        }
        needs.merge(FUEL, steps.size() * batches, Integer::sum);
        return Map.copyOf(needs);
    }

    private static Map<String, String> effects() {
        Map<String, String> effects = new LinkedHashMap<>();
        effects.put("healing", "glistering_melon_slice");
        effects.put("fire_resistance", "magma_cream");
        effects.put("regeneration", "ghast_tear");
        effects.put("strength", "blaze_powder");
        effects.put("swiftness", "sugar");
        effects.put("night_vision", "golden_carrot");
        effects.put("water_breathing", "pufferfish");
        return Map.copyOf(effects);
    }
}
