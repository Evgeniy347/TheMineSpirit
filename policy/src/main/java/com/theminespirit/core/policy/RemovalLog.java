package com.theminespirit.core.policy;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class RemovalLog {
    public static final int SAMPLE = 5;
    private final Map<String, Integer> counts = new LinkedHashMap<>();
    private final List<BlockCoord> sample = new ArrayList<>();

    public void removed(String blockId, BlockCoord cell) {
        Objects.requireNonNull(blockId, "Id снятого блока не может быть null: строка лога называет, что снято");
        Objects.requireNonNull(cell, "Клетка снятого блока не может быть null: строка лога называет, где");
        counts.merge(blockId, 1, Integer::sum);
        if (sample.size() < SAMPLE) {
            sample.add(cell);
        }
    }

    public boolean isEmpty() {
        return counts.isEmpty();
    }

    public int total() {
        int total = 0;
        for (int count : counts.values()) {
            total += count;
        }
        return total;
    }

    public String summary() {
        List<String> parts = new ArrayList<>();
        counts.forEach((id, count) -> parts.add(id + "×" + count));
        return String.join(", ", parts) + " (всего " + total() + ", первые " + sample + ")";
    }
}
