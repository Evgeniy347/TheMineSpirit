package com.theminespirit.core.town;

import java.util.Locale;
import java.util.Objects;

public record Placement(String id, String name, int stage, int x, int z, int width, int depth, Facing facing) {
    public Placement {
        Objects.requireNonNull(id, "id места не может быть null");
        Objects.requireNonNull(name, "Подпись места не может быть null");
        Objects.requireNonNull(facing, "Сторона входа не может быть null: дверь смотрит на улицу");
        if (width < 1 || depth < 1 || stage < 1) {
            throw new IllegalArgumentException("Место '" + id + "' " + width + "×" + depth + " этапа " + stage
                    + ": стороны положительны, этапы — с первого");
        }
    }

    public boolean covers(int px, int pz) {
        return px >= x && px < x + width && pz >= z && pz < z + depth;
    }

    public boolean overlaps(Placement other, int gap) {
        return x - gap < other.x + other.width
                && other.x - gap < x + width
                && z - gap < other.z + other.depth
                && other.z - gap < z + depth;
    }

    public int centerX() {
        return x + width / 2;
    }

    public int centerZ() {
        return z + depth / 2;
    }

    public ColumnMask mask() {
        return ColumnMask.rect(x, z, x + width - 1, z + depth - 1);
    }

    public enum Facing {
        NORTH,
        EAST,
        SOUTH,
        WEST;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
