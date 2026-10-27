package com.theminespirit.core.crafting;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

public record ChainPlan(
        RecipeSpec target,
        int crafts,
        List<ChainStep> steps,
        Map<String, Integer> consumed,
        Map<String, Integer> produced,
        Map<String, Integer> missing) {
    public ChainPlan {
        Objects.requireNonNull(target, "План цепочки без целевого рецепта не описать");
        if (crafts <= 0) {
            throw new IllegalArgumentException("Число крафтов цели должно быть больше 0, а получено " + crafts);
        }
        steps = List.copyOf(Objects.requireNonNull(steps, "Шаги цепочки не могут быть null"));
        consumed = Map.copyOf(Objects.requireNonNull(consumed, "Расход цепочки не может быть null"));
        produced = Map.copyOf(Objects.requireNonNull(produced, "Выход цепочки не может быть null"));
        missing = Map.copyOf(Objects.requireNonNull(missing, "Нехватка цепочки не может быть null"));
    }

    static ChainPlan of(
            RecipeSpec target,
            int crafts,
            Map<String, Integer> initial,
            Map<String, Integer> last,
            List<ChainStep> steps,
            Map<String, Integer> missing) {
        Map<String, Integer> consumed = new TreeMap<>();
        Map<String, Integer> produced = new TreeMap<>();
        Set<String> items = new HashSet<>(initial.keySet());
        items.addAll(last.keySet());
        items.addAll(missing.keySet());
        for (String item : items) {
            int delta = last.getOrDefault(item, 0) - initial.getOrDefault(item, 0);
            if (delta < 0) {
                consumed.put(item, -delta);
            } else if (delta - missing.getOrDefault(item, 0) > 0) {
                produced.put(item, delta - missing.getOrDefault(item, 0));
            }
        }
        return new ChainPlan(target, crafts, steps, consumed, produced, missing);
    }

    public boolean feasible() {
        return missing.isEmpty();
    }

    public int targetCount() {
        return crafts * target.outputCount();
    }

    public Map<String, Integer> leftovers() {
        Map<String, Integer> left = new TreeMap<>(produced);
        int extra = left.getOrDefault(target.outputItem(), 0) - targetCount();
        if (extra > 0) {
            left.put(target.outputItem(), extra);
        } else {
            left.remove(target.outputItem());
        }
        return Map.copyOf(left);
    }
}
