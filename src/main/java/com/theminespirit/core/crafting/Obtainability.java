package com.theminespirit.core.crafting;

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
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public final class Obtainability {
    private static final String MINECRAFT = "minecraft:";

    private final Function<String, Optional<RecipeSpec>> craft;
    private final Function<String, List<RecipeSpec>> smelt;
    private final Predicate<String> gatherable;
    private final Map<String, Boolean> known = new HashMap<>();

    public Obtainability(
            Function<String, Optional<RecipeSpec>> craft,
            Function<String, List<RecipeSpec>> smelt,
            Predicate<String> gatherable) {
        this.craft = Objects.requireNonNull(craft, "Рецепты крафта не могут быть null: без них не проверить путь");
        this.smelt = Objects.requireNonNull(smelt, "Рецепты плавки не могут быть null: без них не проверить путь");
        this.gatherable =
                Objects.requireNonNull(gatherable, "Проверка добычи не может быть null: без неё нет основания");
    }

    public boolean canObtain(String item) {
        String key = key(item);
        Boolean cached = known.get(key);
        if (cached != null) {
            return cached;
        }
        closure(key);
        return known.getOrDefault(key, false);
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
            craft.apply(item).ifPresent(recipe -> ways.add(slots(recipe)));
            for (RecipeSpec recipe : smelt.apply(item)) {
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
            if (gatherable.test(item)) {
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
            known.put(item, found.contains(item));
        }
    }

    private static List<Set<String>> slots(RecipeSpec recipe) {
        List<Set<String>> slots = new ArrayList<>();
        for (IngredientSpec slot : recipe.ingredients()) {
            Set<String> alternatives = new HashSet<>();
            for (String alternative : slot.alternatives()) {
                alternatives.add(key(alternative));
            }
            slots.add(alternatives);
        }
        return slots;
    }

    private static String key(String item) {
        Objects.requireNonNull(item, "Имя предмета не может быть null: по нему ищут путь");
        return item.startsWith(MINECRAFT) ? item.substring(MINECRAFT.length()) : item;
    }
}
