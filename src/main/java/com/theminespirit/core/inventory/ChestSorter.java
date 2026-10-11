package com.theminespirit.core.inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ChestSorter {
    private ChestSorter() {
    }

    public static List<Stack> sort(List<Stack> slots, ValueTable values) {
        Objects.requireNonNull(slots, "Слоты контейнера не могут быть null: сортировать нечего");
        Objects.requireNonNull(values, "Таблица ценностей не может быть null: "
                + "по ней сортёр расставляет группы предметов");
        List<Stack> present = new ArrayList<>(slots.size());
        for (Stack stack : slots) {
            if (stack != null) {
                present.add(stack);
            }
        }
        present.sort(order(values));
        return merge(present);
    }

    private static Comparator<Stack> order(ValueTable values) {
        Map<String, Integer> ranks = new HashMap<>();
        List<Category> categories = values.categories();
        for (int index = 0; index < categories.size(); index++) {
            ranks.put(categories.get(index).name(), index);
        }
        return Comparator
                .comparingInt((Stack stack) -> ranks.get(values.categoryOf(stack.item())))
                .thenComparing(Stack::item)
                .thenComparingInt(Stack::variant)
                .thenComparing(Comparator.comparingInt(Stack::count).reversed());
    }

    private static List<Stack> merge(List<Stack> sorted) {
        List<Stack> layout = new ArrayList<>(sorted.size());
        int index = 0;
        while (index < sorted.size()) {
            Stack first = sorted.get(index);
            int total = 0;
            while (index < sorted.size()) {
                Stack stack = sorted.get(index);
                if (!stack.sameKindAs(first)) {
                    break;
                }
                total += stack.count();
                index++;
            }
            while (total > 0) {
                int count = Math.min(total, first.maxStack());
                layout.add(new Stack(first.item(), first.variant(), count, first.maxStack()));
                total -= count;
            }
        }
        return layout;
    }
}
