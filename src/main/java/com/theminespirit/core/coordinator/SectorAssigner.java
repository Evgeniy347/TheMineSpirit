package com.theminespirit.core.coordinator;

import com.theminespirit.core.tactics.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class SectorAssigner {
    private static final long BEARING_BITS = 0x1FFFFFFFFFFFL;
    private static final double UNIT = 0x20000000000L;

    public SectorAssigner() {}

    public List<DefenseAssignment> assign(Point center, double radius, List<UUID> defenders) {
        if (center == null) {
            throw new IllegalArgumentException("center не может быть null: круг без центра не построить");
        }
        if (radius <= 0) {
            throw new IllegalArgumentException(
                    "radius = " + radius + ": радиус обороны обязан быть положительным, иначе сектора вырождены");
        }
        if (defenders == null) {
            throw new IllegalArgumentException("defenders не может быть null: нет защитников — пустой список");
        }
        if (defenders.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("defenders содержит null: защитник без id не получает пост");
        }
        if (defenders.isEmpty()) {
            return List.of();
        }
        List<UUID> ordered = defenders.stream()
                .sorted(Comparator.comparingDouble(SectorAssigner::bearing).thenComparing(UUID::compareTo))
                .toList();
        int count = ordered.size();
        double slice = 2 * Math.PI / count;
        List<DefenseAssignment> assignments = new ArrayList<>(count);
        for (int rank = 0; rank < count; rank++) {
            double direction = rank * slice;
            Point post = new Point(
                    center.x() + Math.cos(direction) * radius, center.y(), center.z() + Math.sin(direction) * radius);
            assignments.add(new DefenseAssignment(ordered.get(rank), post, direction));
        }
        return List.copyOf(assignments);
    }

    private static double bearing(UUID defender) {
        return 2 * Math.PI * ((defender.getMostSignificantBits() & BEARING_BITS) / UNIT);
    }
}
