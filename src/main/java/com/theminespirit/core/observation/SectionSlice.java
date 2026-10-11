package com.theminespirit.core.observation;

import java.util.Objects;

public record SectionSlice(int sectionX, int sectionY, int sectionZ, BlockBox bounds) {
    public SectionSlice {
        Objects.requireNonNull(bounds, "bounds не может быть null: срез описывает часть куба внутри секции");
    }

    public long volume() {
        return bounds.volume();
    }

    public long distanceSquared(BlockCoord from) {
        long dx = gap(from.x(), bounds.min().x(), bounds.max().x());
        long dy = gap(from.y(), bounds.min().y(), bounds.max().y());
        long dz = gap(from.z(), bounds.min().z(), bounds.max().z());
        return dx * dx + dy * dy + dz * dz;
    }

    private static long gap(int value, int low, int high) {
        if (value < low) {
            return (long) low - value;
        }
        if (value > high) {
            return (long) value - high;
        }
        return 0;
    }
}
