package com.theminespirit.core.inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class ChestSorter {
    private ChestSorter() {
    }

    public static List<Stack> sort(List<Stack> slots, ValueTable values) {
        Objects.requireNonNull(slots, "Слоты контейнера не могут быть null: сортировать нечего");
        Objects.requireNonNull(values, "Таблица ценностей не может быть null: "
                + "по ней сортёр расставляет группы предметов");
        List<Ranked> ranked = new ArrayList<>(slots.size());
        for (Stack stack : slots) {
            if (stack != null) {
                ranked.add(new Ranked(stack, values.categoryRank(stack.item())));
            }
        }
        ranked.sort(Comparator.comparingInt((Ranked slot) -> slot.rank)
                .thenComparing(slot -> slot.stack.item())
                .thenComparingInt(slot -> slot.stack.variant())
                .thenComparing(Comparator.comparingInt((Ranked slot) -> slot.stack.count()).reversed()));
        return merge(ranked);
    }

    private static List<Stack> merge(List<Ranked> sorted) {
        List<Stack> layout = new ArrayList<>(sorted.size());
        int index = 0;
        while (index < sorted.size()) {
            Stack first = sorted.get(index).stack;
            int total = 0;
            while (index < sorted.size()) {
                Ranked slot = sorted.get(index);
                if (!slot.stack.sameKindAs(first)) {
                    break;
                }
                total += slot.stack.count();
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

    private record Ranked(Stack stack, int rank) {
    }
}
