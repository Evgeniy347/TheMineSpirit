package com.theminespirit.neoforge.crafting;

import com.theminespirit.core.crafting.IngredientSpec;
import com.theminespirit.core.crafting.RecipeSpec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class CraftObtainability {
    private final RecipeKnowledge knowledge;
    private final Map<String, Boolean> obtainable = new HashMap<>();

    CraftObtainability(RecipeKnowledge knowledge) {
        this.knowledge =
                Objects.requireNonNull(knowledge, "Знания рецептов не могут быть null: без них нечего проверять");
    }

    boolean canObtain(String item) {
        String key = ItemIds.normalize(item);
        Boolean cached = obtainable.get(key);
        if (cached != null) {
            return cached;
        }
        closure(key);
        return obtainable.getOrDefault(key, false);
    }

    private void closure(String root) {
        Map<String, List<List<Set<String>>>> recipes = new LinkedHashMap<>();
        Set<String> seen = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            String item = queue.poll();
            if (!seen.add(item)) {
                continue;
            }
            List<List<Set<String>>> ways = new ArrayList<>();
            knowledge.craftRecipe(item, Map.of()).ifPresent(recipe -> ways.add(slots(recipe)));
            for (RecipeSpec recipe : knowledge.smeltRecipes(item)) {
                ways.add(slots(recipe));
            }
            recipes.put(item, ways);
            for (List<Set<String>> way : ways) {
                for (Set<String> slot : way) {
                    for (String alternative : slot) {
                        if (!seen.contains(alternative)) {
                            queue.add(alternative);
                        }
                    }
                }
            }
        }
        Set<String> found = new HashSet<>();
        for (String item : seen) {
            if (RecipeKnowledge.gatherable(item)) {
                found.add(item);
            }
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String item : seen) {
                if (found.contains(item)) {
                    continue;
                }
                for (List<Set<String>> way : recipes.getOrDefault(item, List.of())) {
                    if (way.stream().allMatch(slot -> slot.stream().anyMatch(found::contains))) {
                        found.add(item);
                        changed = true;
                        break;
                    }
                }
            }
        }
        for (String item : seen) {
            obtainable.put(item, found.contains(item));
        }
    }

    private static List<Set<String>> slots(RecipeSpec recipe) {
        List<Set<String>> slots = new ArrayList<>();
        for (IngredientSpec slot : recipe.ingredients()) {
            Set<String> alternatives = new HashSet<>();
            for (String alternative : slot.alternatives()) {
                alternatives.add(ItemIds.normalize(alternative));
            }
            slots.add(alternatives);
        }
        return slots;
    }
}
