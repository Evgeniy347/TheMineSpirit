package com.theminespirit.core.building;

import java.util.Objects;

/**
 * Правило опоры блока: опорой считается клетка снизу с крепкой верхней гранью или сосед сбоку,
 * крепкий к целевой клетке гранью. Одно правило для постановки в мире и для проверки схем.
 */
public final class BlockSupport {
    public enum Face {
        UP,
        DOWN,
        NORTH,
        SOUTH,
        EAST,
        WEST
    }

    @FunctionalInterface
    public interface World {
        boolean sturdy(int x, int y, int z, Face face);
    }

    private BlockSupport() {}

    public static boolean hasSupport(World world, int x, int y, int z) {
        Objects.requireNonNull(world, "Мир не может быть null: без него неизвестно, на что опирается блок");
        if (world.sturdy(x, y - 1, z, Face.UP)) {
            return true;
        }
        if (world.sturdy(x, y, z - 1, Face.SOUTH)) {
            return true;
        }
        if (world.sturdy(x, y, z + 1, Face.NORTH)) {
            return true;
        }
        if (world.sturdy(x - 1, y, z, Face.EAST)) {
            return true;
        }
        return world.sturdy(x + 1, y, z, Face.WEST);
    }

    public static boolean hasHangingSupport(World world, int x, int y, int z) {
        Objects.requireNonNull(world, "Мир не может быть null: без него неизвестно, на чём висит блок");
        return world.sturdy(x, y + 1, z, Face.DOWN);
    }
}
