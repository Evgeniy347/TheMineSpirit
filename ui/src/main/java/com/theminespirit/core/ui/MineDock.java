package com.theminespirit.core.ui;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class MineDock {
    private MineDock() {}

    public static Map<MineSnapshot.Kind, Integer> counts(MineSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "Срез шахты не может быть null: пустой срез — пустая карта");
        Map<MineSnapshot.Kind, Integer> counts = new EnumMap<>(MineSnapshot.Kind.class);
        for (MineSnapshot.Kind kind : MineSnapshot.Kind.values()) {
            counts.put(kind, 0);
        }
        for (MineSnapshot.Cell cell : snapshot.cells()) {
            counts.merge(cell.kind(), 1, Integer::sum);
        }
        return Map.copyOf(counts);
    }
}
