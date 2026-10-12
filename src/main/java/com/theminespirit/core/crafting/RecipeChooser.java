package com.theminespirit.core.crafting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class RecipeChooser {
    private static final Comparator<RecipeSpec> CHEAPEST_PER_UNIT_THEN_ID = (first, second) -> {
        long firstCost = (long) first.ingredients().size() * second.outputCount();
        long secondCost = (long) second.ingredients().size() * first.outputCount();
        int byCost = Long.compare(firstCost, secondCost);
        return byCost != 0 ? byCost : first.id().compareTo(second.id());
    };

    private RecipeChooser() {
    }

    public static Optional<RecipeSpec> pick(List<RecipeSpec> candidates, Map<String, Integer> available) {
        Objects.requireNonNull(candidates, "candidates не может быть null: выбирать не из чего");
        Objects.requireNonNull(available, "available не может быть null: без наличия нельзя проверить выполнимость");
        List<RecipeSpec> feasible = new ArrayList<>();
        for (RecipeSpec candidate : candidates) {
            if (IngredientAssigner.assign(candidate.ingredients(), available).isPresent()) {
                feasible.add(candidate);
            }
        }
        List<RecipeSpec> pool = feasible.isEmpty() ? candidates : feasible;
        return pool.stream().min(CHEAPEST_PER_UNIT_THEN_ID);
    }
}
