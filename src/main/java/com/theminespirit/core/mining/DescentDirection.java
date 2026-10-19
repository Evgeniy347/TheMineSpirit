package com.theminespirit.core.mining;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public enum DescentDirection {
    NORTH(0, -1),
    SOUTH(0, 1),
    EAST(1, 0),
    WEST(-1, 0);

    private static final List<String> NAMES = Arrays.stream(values())
            .map(direction -> direction.name().toLowerCase(Locale.ROOT))
            .toList();

    public final int dx;
    public final int dz;

    DescentDirection(int dx, int dz) {
        this.dx = dx;
        this.dz = dz;
    }

    public static List<String> names() {
        return NAMES;
    }

    public static DescentDirection parse(String id) {
        Objects.requireNonNull(id, "Направление спуска не может быть null: без него неизвестно, куда копать");
        try {
            return valueOf(id.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(
                    "Неизвестное направление спуска '" + id + "': допустимы " + NAMES, error);
        }
    }
}
