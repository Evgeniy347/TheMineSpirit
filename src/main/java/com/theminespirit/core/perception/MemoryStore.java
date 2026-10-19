package com.theminespirit.core.perception;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public record MemoryStore(List<MemoryPoint> points) {
    public static final int MAX_PER_TYPE = 16;
    private static final MemoryStore EMPTY = new MemoryStore(List.of());

    public MemoryStore {
        Objects.requireNonNull(points, "points не может быть null: пустая память — пустой список");
        points = List.copyOf(points);
        requireUniqueNames(points);
        requireWithinLimit(points);
    }

    public static MemoryStore empty() {
        return EMPTY;
    }

    public MemoryStore remember(MemoryPoint point) {
        Objects.requireNonNull(point, "point не может быть null: запоминать нечего");
        List<MemoryPoint> updated = new ArrayList<>(points);
        int existing = indexOf(point.name());
        if (existing >= 0) {
            updated.set(existing, point);
        } else {
            updated.add(point);
        }
        return new MemoryStore(updated);
    }

    public @Nullable MemoryPoint recall(String name) {
        int index = indexOf(Objects.requireNonNull(name, "name не может быть null: точку вспоминают по имени"));
        return index < 0 ? null : points.get(index);
    }

    public List<MemoryPoint> recallAll(MemoryType type) {
        Objects.requireNonNull(type, "type не может быть null: точки вспоминают по типу");
        return points.stream().filter(point -> point.type() == type).toList();
    }

    public MemoryStore forget(String name) {
        int index = indexOf(Objects.requireNonNull(name, "name не может быть null: точку забывают по имени"));
        if (index < 0) {
            return this;
        }
        List<MemoryPoint> remaining = new ArrayList<>(points);
        remaining.remove(index);
        return new MemoryStore(remaining);
    }

    public List<MemoryPoint> all() {
        return points;
    }

    private int indexOf(String name) {
        for (int i = 0; i < points.size(); i++) {
            if (points.get(i).name().equals(name)) {
                return i;
            }
        }
        return -1;
    }

    private static void requireUniqueNames(List<MemoryPoint> points) {
        Set<String> seen = new HashSet<>();
        for (MemoryPoint point : points) {
            if (!seen.add(point.name())) {
                throw new IllegalArgumentException("Имя точки '" + point.name() + "' встречается в памяти дважды: "
                        + "имя однозначно определяет точку, повтор — это замена через remember");
            }
        }
    }

    private static void requireWithinLimit(List<MemoryPoint> points) {
        for (MemoryType type : MemoryType.values()) {
            long count = points.stream().filter(point -> point.type() == type).count();
            if (count > MAX_PER_TYPE) {
                throw new MemoryFullException(type, MAX_PER_TYPE);
            }
        }
    }
}
