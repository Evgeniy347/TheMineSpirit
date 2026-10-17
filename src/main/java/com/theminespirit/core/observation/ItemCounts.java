package com.theminespirit.core.observation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Сводит повторы одного предмета в одну запись и сортирует по id: одинаковый набор даёт одинаковый список. */
public final class ItemCounts {
    private ItemCounts() {
    }

    public static List<ItemCount> aggregate(List<ItemCount> entries, String field) {
        Objects.requireNonNull(entries, field + " не может быть null: пустой список передаётся как список без элементов");
        Map<String, Integer> merged = new LinkedHashMap<>();
        for (ItemCount entry : entries) {
            Objects.requireNonNull(entry, field + " не может содержать null: список описывает только существующие предметы");
            merged.merge(entry.item(), entry.count(), Integer::sum);
        }
        List<ItemCount> result = new ArrayList<>(merged.size());
        merged.forEach((item, count) -> result.add(new ItemCount(item, count)));
        result.sort(Comparator.comparing(ItemCount::item));
        return List.copyOf(result);
    }
}
