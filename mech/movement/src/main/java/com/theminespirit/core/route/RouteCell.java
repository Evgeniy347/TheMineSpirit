package com.theminespirit.core.route;

import com.theminespirit.core.mining.Cell;

public final class RouteCell {
    public static final int AIR = 0;
    public static final int CLIMB = 1;
    public static final int WATER = 2;
    public static final int LAVA = 3;
    public static final int SOLID = 4;
    public static final int BLOCKED = 5;
    public static final int TALL = 6;
    private static final int KIND_BITS = 3;
    private static final int KIND_MASK = (1 << KIND_BITS) - 1;
    private static final int MAX_TICKS = Integer.MAX_VALUE >> KIND_BITS;

    private RouteCell() {}

    public static int solid(int breakTicks) {
        return encode(SOLID, breakTicks);
    }

    public static int tall(int breakTicks) {
        return encode(TALL, breakTicks);
    }

    public static int kind(int cell) {
        return cell & KIND_MASK;
    }

    public static Cell safety(int cell) {
        return switch (kind(cell)) {
            case SOLID, BLOCKED, TALL -> Cell.SOLID;
            case AIR, CLIMB -> Cell.PASSABLE;
            case WATER -> Cell.WATER;
            default -> Cell.LAVA;
        };
    }

    private static int encode(int kind, int breakTicks) {
        if (breakTicks < 0) {
            throw new IllegalArgumentException("Срок ломки " + breakTicks
                    + " тиков отрицателен: неразрушимый блок задаётся как BLOCKED, а не отрицательным сроком");
        }
        return kind | (Math.min(breakTicks, MAX_TICKS) << KIND_BITS);
    }
}
