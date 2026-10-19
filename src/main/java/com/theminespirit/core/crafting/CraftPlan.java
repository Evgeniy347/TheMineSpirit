package com.theminespirit.core.crafting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public record CraftPlan(
        RecipeSpec recipe,
        int crafts,
        List<String> assignment,
        Map<String, Integer> required,
        Map<String, Integer> missing) {
    public CraftPlan {
        Objects.requireNonNull(recipe, "recipe не может быть null: план строится по рецепту");
        if (crafts <= 0) {
            throw new IllegalArgumentException("Число крафтов должно быть больше 0, а получено " + crafts);
        }
        assignment = List.copyOf(assignment);
        required = Map.copyOf(required);
        missing = Map.copyOf(missing);
    }

    public static CraftPlan of(RecipeSpec recipe, int count, Map<String, Integer> available) {
        Objects.requireNonNull(recipe, "recipe не может быть null: план строится по рецепту");
        Objects.requireNonNull(available, "available не может быть null: без наличия нельзя найти нехватку");
        if (count <= 0) {
            throw new IllegalArgumentException(
                    "Количество предметов должно быть больше 0, а получено " + count + ": крафтить ноль нечего");
        }
        int crafts = (int) (((long) count + recipe.outputCount() - 1) / recipe.outputCount());
        List<IngredientSpec> slots = repeated(recipe.ingredients(), crafts);
        Optional<List<String>> assignment = IngredientAssigner.assign(slots, available);
        if (assignment.isPresent()) {
            return new CraftPlan(recipe, crafts, assignment.get(), tally(assignment.get()), Map.of());
        }
        Map<String, Integer> wished = wishedDemand(slots, available);
        return new CraftPlan(recipe, crafts, List.of(), wished, shortage(wished, available));
    }

    public boolean feasible() {
        return missing.isEmpty();
    }

    public List<String> itemsForCraft(int craft) {
        if (craft < 0 || craft >= crafts) {
            throw new IllegalArgumentException("Крафт " + craft + " вне диапазона [0, " + crafts + ")");
        }
        if (assignment.isEmpty()) {
            throw new IllegalStateException("План невыполним: назначения предметов нет, проверьте feasible()");
        }
        int size = recipe.ingredients().size();
        return assignment.subList(craft * size, (craft + 1) * size);
    }

    private static List<IngredientSpec> repeated(List<IngredientSpec> ingredients, int times) {
        List<IngredientSpec> slots = new ArrayList<>(ingredients.size() * times);
        for (int i = 0; i < times; i++) {
            slots.addAll(ingredients);
        }
        return slots;
    }

    private static Map<String, Integer> tally(List<String> items) {
        Map<String, Integer> totals = new TreeMap<>();
        for (String item : items) {
            totals.merge(item, 1, Integer::sum);
        }
        return totals;
    }

    private static Map<String, Integer> wishedDemand(List<IngredientSpec> slots, Map<String, Integer> available) {
        Map<String, Integer> left = new HashMap<>(available);
        Map<String, Integer> wished = new TreeMap<>();
        for (IngredientSpec slot : slots) {
            String best = slot.alternatives().get(0);
            for (String alternative : slot.alternatives()) {
                if (left.getOrDefault(alternative, 0) > left.getOrDefault(best, 0)) {
                    best = alternative;
                }
            }
            left.merge(best, -1, Integer::sum);
            wished.merge(best, 1, Integer::sum);
        }
        return wished;
    }

    private static Map<String, Integer> shortage(Map<String, Integer> wished, Map<String, Integer> available) {
        Map<String, Integer> missing = new TreeMap<>();
        wished.forEach((item, need) -> {
            int lack = need - available.getOrDefault(item, 0);
            if (lack > 0) {
                missing.put(item, lack);
            }
        });
        return missing;
    }
}
