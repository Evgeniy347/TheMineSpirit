package com.theminespirit.core.crafting;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public final class RecipeReachability {
    private static final int MAX_DEPTH = 64;

    private final RecipeIndex index;
    private final Predicate<String> gatherable;
    private final Map<String, Boolean> memo = new HashMap<>();

    public RecipeReachability(RecipeIndex index, Predicate<String> gatherable) {
        this.index = index;
        this.gatherable = gatherable;
    }

    public boolean canObtain(String item) {
        return canObtain(item, new HashSet<>(), 0);
    }

    private boolean canObtain(String item, Set<String> path, int depth) {
        String key = normalize(item);
        Boolean cached = memo.get(key);
        if (cached != null) {
            return cached;
        }
        if (depth > MAX_DEPTH || !path.add(key)) {
            return false;
        }
        boolean result;
        try {
            if (gatherable.test(key)) {
                result = true;
            } else {
                Optional<RecipeSpec> craft = RecipeChooser.pick(index.craftsFor(key), Map.of());
                if (craft.isPresent()) {
                    result = craft.get().ingredients().stream()
                            .allMatch(slot ->
                                    slot.alternatives().stream().anyMatch(alt -> canObtain(alt, path, depth + 1)));
                } else {
                    result = index.smeltingFor(key).stream()
                            .anyMatch(recipe -> recipe.ingredients().get(0).alternatives().stream()
                                    .anyMatch(alt -> canObtain(alt, path, depth + 1)));
                }
            }
        } finally {
            path.remove(key);
        }
        memo.put(key, result);
        return result;
    }

    private static String normalize(String item) {
        return item.startsWith("minecraft:") ? item.substring("minecraft:".length()) : item;
    }
}
