package com.theminespirit.core.crafting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class IngredientAssigner {
    private final List<IngredientSpec> ingredients;
    private final Map<String, Integer> available;
    private final String[] chosen;
    private final Map<String, List<Integer>> holders = new HashMap<>();
    private Set<String> visited = new HashSet<>();

    private IngredientAssigner(List<IngredientSpec> ingredients, Map<String, Integer> available) {
        this.ingredients = ingredients;
        this.available = available;
        this.chosen = new String[ingredients.size()];
    }

    public static Optional<List<String>> assign(List<IngredientSpec> ingredients, Map<String, Integer> available) {
        Objects.requireNonNull(ingredients, "ingredients не может быть null: нечего раскладывать по местам");
        Objects.requireNonNull(available, "available не может быть null: без наличия нечем заполнить места");
        IngredientAssigner assigner = new IngredientAssigner(ingredients, available);
        for (int slot = 0; slot < ingredients.size(); slot++) {
            assigner.visited = new HashSet<>();
            if (!assigner.place(slot)) {
                return Optional.empty();
            }
        }
        return Optional.of(List.copyOf(Arrays.asList(assigner.chosen)));
    }

    private boolean place(int slot) {
        List<String> alternatives = ingredients.get(slot).alternatives();
        for (String item : alternatives) {
            if (holdersOf(item).size() < capacity(item)) {
                put(slot, item);
                return true;
            }
        }
        for (String item : alternatives) {
            if (capacity(item) > 0 && visited.add(item) && displaceFrom(slot, item)) {
                return true;
            }
        }
        return false;
    }

    private boolean displaceFrom(int slot, String item) {
        for (int holder : new ArrayList<>(holdersOf(item))) {
            if (place(holder)) {
                holdersOf(item).remove(Integer.valueOf(holder));
                put(slot, item);
                return true;
            }
        }
        return false;
    }

    private void put(int slot, String item) {
        chosen[slot] = item;
        holdersOf(item).add(slot);
    }

    private List<Integer> holdersOf(String item) {
        return holders.computeIfAbsent(item, key -> new ArrayList<>());
    }

    private int capacity(String item) {
        return available.getOrDefault(item, 0);
    }
}
