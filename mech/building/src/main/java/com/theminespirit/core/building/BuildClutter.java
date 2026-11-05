package com.theminespirit.core.building;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class BuildClutter {
    private BuildClutter() {}

    public static List<Integer> junk(List<@Nullable String> slots, Set<String> needed, Set<Integer> keep) {
        Objects.requireNonNull(slots, "Слоты инвентаря не могут быть null: пустой слот задаётся null в списке");
        Objects.requireNonNull(needed, "Нужное стройке не может быть null: пустое задаётся пустым множеством");
        Objects.requireNonNull(keep, "Сохраняемые слоты не могут быть null: пустое задаётся пустым множеством");
        List<Integer> junk = new ArrayList<>();
        for (int slot = 0; slot < slots.size(); slot++) {
            String item = slots.get(slot);
            if (item != null && !needed.contains(item) && !keep.contains(slot)) {
                junk.add(slot);
            }
        }
        return List.copyOf(junk);
    }

    public static List<Integer> toRelease(List<Integer> junk, int freeSlots, int stacksWanted) {
        Objects.requireNonNull(junk, "Лишнее не может быть null: пустое задаётся пустым списком");
        if (freeSlots > 0) {
            return List.of();
        }
        int missing = Math.max(0, stacksWanted);
        return List.copyOf(junk.subList(0, Math.min(junk.size(), missing)));
    }
}
