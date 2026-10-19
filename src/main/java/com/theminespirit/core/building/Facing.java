package com.theminespirit.core.building;

import java.util.Locale;
import java.util.Optional;

/** Горизонтальное направление состояния блока ({@code facing}) в осях схемы: север — −Z, восток — +X. */
public enum Facing {
    NORTH(0, -1),
    EAST(1, 0),
    SOUTH(0, 1),
    WEST(-1, 0);

    private final int dx;
    private final int dz;

    Facing(int dx, int dz) {
        this.dx = dx;
        this.dz = dz;
    }

    public int dx() {
        return dx;
    }

    public int dz() {
        return dz;
    }

    public Facing clockwise() {
        return switch (this) {
            case NORTH -> EAST;
            case EAST -> SOUTH;
            case SOUTH -> WEST;
            case WEST -> NORTH;
        };
    }

    public Facing counterClockwise() {
        return clockwise().opposite();
    }

    public Facing opposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case EAST -> WEST;
            case SOUTH -> NORTH;
            case WEST -> EAST;
        };
    }

    public static Optional<Facing> of(String name) {
        for (Facing facing : values()) {
            if (facing.name().toLowerCase(Locale.ROOT).equals(name)) {
                return Optional.of(facing);
            }
        }
        return Optional.empty();
    }

    public static Optional<Facing> ofVector(int dx, int dz) {
        for (Facing facing : values()) {
            if (facing.dx == dx && facing.dz == dz) {
                return Optional.of(facing);
            }
        }
        return Optional.empty();
    }
}
