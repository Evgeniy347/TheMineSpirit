package com.theminespirit.core.crafting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public final class RecipeIndex {
    private final Map<String, List<RecipeSpec>> crafts;
    private final Map<String, List<RecipeSpec>> smelts;
    private final int buildSteps;
    private final AtomicInteger lookupProbes = new AtomicInteger();

    private RecipeIndex(Map<String, List<RecipeSpec>> crafts, Map<String, List<RecipeSpec>> smelts, int buildSteps) {
        this.crafts = crafts;
        this.smelts = smelts;
        this.buildSteps = buildSteps;
    }

    public static RecipeIndex of(List<RecipeSpec> recipes) {
        Objects.requireNonNull(recipes, "recipes не может быть null: индексу нечего хранить");
        Map<String, List<RecipeSpec>> crafts = new HashMap<>();
        Map<String, List<RecipeSpec>> smelts = new HashMap<>();
        int steps = 0;
        for (RecipeSpec recipe : recipes) {
            steps++;
            Map<String, List<RecipeSpec>> target = recipe.kind() == RecipeKind.SMELTING ? smelts : crafts;
            target.computeIfAbsent(recipe.outputItem(), key -> new ArrayList<>(1)).add(recipe);
        }
        return new RecipeIndex(crafts, smelts, steps);
    }

    public List<RecipeSpec> craftsFor(String product) {
        lookupProbes.incrementAndGet();
        return crafts.getOrDefault(product, List.of());
    }

    public List<RecipeSpec> smeltingFor(String product) {
        lookupProbes.incrementAndGet();
        return smelts.getOrDefault(product, List.of());
    }

    public int buildSteps() {
        return buildSteps;
    }

    public int lookupProbes() {
        return lookupProbes.get();
    }
}
