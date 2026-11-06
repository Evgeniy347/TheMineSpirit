package com.theminespirit.core.board;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ChestShare {
    private ChestShare() {}

    public static List<Integer> takes(List<Integer> nearestFirst, int keep, int want) {
        Objects.requireNonNull(nearestFirst, "Запас сундуков не может быть null: нет сундуков — пустой список");
        if (keep < 0 || want < 0) {
            throw new IllegalArgumentException("Минимум " + keep + " и нужда " + want + " не отрицательные");
        }
        int total = nearestFirst.stream().mapToInt(Integer::intValue).sum();
        int left = Math.min(want, Math.max(0, total - keep));
        List<Integer> takes = new ArrayList<>(nearestFirst.size());
        for (int count : nearestFirst) {
            int take = Math.min(left, count);
            takes.add(take);
            left -= take;
        }
        return List.copyOf(takes);
    }
}
