package com.theminespirit.core.building;

import java.util.Locale;
import java.util.Objects;

/**
 * Поворот постройки на четверть оборота (90°) вокруг вертикальной оси. Позиции блока и его состояние
 * крутятся одной и той же четвертью, иначе дверь или калитка встанут не в ту сторону.
 *
 * <p>Формулы выведены для вида сверху: +X — восток, +Z — юг, четверть по часовой стрелке переводит
 * север в восток. Клетка задаётся внутри собственной коробки схемы (0..size-1), поэтому при повороте
 * на 90° стороны схемы X и Z меняются местами.
 */
public enum BuildRotation {
    NONE(0),
    CLOCKWISE_90(1),
    CLOCKWISE_180(2),
    COUNTERCLOCKWISE_90(3);

    private final int quarterTurns;

    BuildRotation(int quarterTurns) {
        this.quarterTurns = quarterTurns;
    }

    public int quarterTurns() {
        return quarterTurns;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static BuildRotation ofQuarterTurns(int turns) {
        int normalized = Math.floorMod(turns, 4);
        for (BuildRotation rotation : values()) {
            if (rotation.quarterTurns == normalized) {
                return rotation;
            }
        }
        throw new IllegalStateException("Четверть " + turns + " не сопоставлена ни одному повороту");
    }

    public static BuildRotation parse(String text) {
        Objects.requireNonNull(text, "Поворот не может быть null: без него неизвестно, как ставить постройку");
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "", "0", "none" -> NONE;
            case "90", "clockwise_90" -> CLOCKWISE_90;
            case "180", "clockwise_180" -> CLOCKWISE_180;
            case "270", "-90", "counterclockwise_90" -> COUNTERCLOCKWISE_90;
            default ->
                throw new IllegalArgumentException("Неизвестный поворот '" + text
                        + "': допустимы 0, 90, 180, 270 или none, clockwise_90, clockwise_180, counterclockwise_90");
        };
    }

    public int rotatedSizeX(int sizeX, int sizeZ) {
        return quarterTurns % 2 == 0 ? sizeX : sizeZ;
    }

    public int rotatedSizeZ(int sizeX, int sizeZ) {
        return quarterTurns % 2 == 0 ? sizeZ : sizeX;
    }

    public Cell rotate(int dx, int dz, int sizeX, int sizeZ) {
        return switch (this) {
            case NONE -> new Cell(dx, dz);
            case CLOCKWISE_90 -> new Cell(sizeZ - 1 - dz, dx);
            case CLOCKWISE_180 -> new Cell(sizeX - 1 - dx, sizeZ - 1 - dz);
            case COUNTERCLOCKWISE_90 -> new Cell(dz, sizeX - 1 - dx);
        };
    }

    public static DirectionVector rotateDirection(int dx, int dz, int quarterTurns) {
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 1 -> new DirectionVector(-dz, dx);
            case 2 -> new DirectionVector(-dx, -dz);
            case 3 -> new DirectionVector(dz, -dx);
            default -> new DirectionVector(dx, dz);
        };
    }

    public record Cell(int x, int z) {}

    public record DirectionVector(int dx, int dz) {
        public boolean equalsVector(int otherDx, int otherDz) {
            return dx == otherDx && dz == otherDz;
        }
    }
}
