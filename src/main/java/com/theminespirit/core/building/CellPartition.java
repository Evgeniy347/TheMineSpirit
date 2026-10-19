package com.theminespirit.core.building;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Делит упорядоченный список клеток схемы между духами стройки. Порядок берётся от вызывающего: у схемы он
 * снизу вверх по слоям, поэтому чередование {@code index % parts} даёт каждому духу клетки во всех слоях, а
 * внутри доли порядок остаётся снизу вверх. Доли не пересекаются и вместе покрывают весь список.
 */
public final class CellPartition {
    private CellPartition() {}

    public static <T> List<List<T>> split(List<T> ordered, int parts) {
        Objects.requireNonNull(ordered, "Список клеток не может быть null: пустое разбиение задают пустым списком");
        if (parts < 1) {
            throw new IllegalArgumentException("Число частей " + parts
                    + ": разбиение клеток требует хотя бы одной доли, иначе клетки некому отдать");
        }
        List<List<T>> shares = new ArrayList<>(parts);
        for (int part = 0; part < parts; part++) {
            shares.add(new ArrayList<>());
        }
        for (int index = 0; index < ordered.size(); index++) {
            shares.get(index % parts).add(ordered.get(index));
        }
        List<List<T>> frozen = new ArrayList<>(parts);
        for (List<T> share : shares) {
            frozen.add(List.copyOf(share));
        }
        return List.copyOf(frozen);
    }
}
