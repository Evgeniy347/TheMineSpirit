package com.theminespirit.core.town;

import com.theminespirit.core.observation.BlockCoord;
import java.util.BitSet;
import java.util.Objects;

public final class TerrainGrid {
    public static final int NO_GROUND = Integer.MIN_VALUE;
    private final int minX;
    private final int minZ;
    private final int width;
    private final int depth;
    private final int[] feet;
    private final BitSet water;

    public TerrainGrid(int minX, int minZ, int width, int depth, int[] feet, BitSet water) {
        Objects.requireNonNull(feet, "Высоты колонок не могут быть null: без рельефа контура участка нет");
        Objects.requireNonNull(water, "Клетки воды не могут быть null: без воды пусто задают пустым набором");
        if (width < 1 || depth < 1 || feet.length != width * depth) {
            throw new IllegalArgumentException("Рельеф " + width + "×" + depth + " с " + feet.length
                    + " высотами: на каждую колонку ровно одна высота, иначе контур поедет");
        }
        this.minX = minX;
        this.minZ = minZ;
        this.width = width;
        this.depth = depth;
        this.feet = feet.clone();
        this.water = (BitSet) water.clone();
    }

    public static TerrainGrid around(BlockCoord anchor, int radius, int[] feet, BitSet water) {
        Objects.requireNonNull(anchor, "Якорь не может быть null: рельеф снимается вокруг него");
        int side = 2 * radius + 1;
        return new TerrainGrid(anchor.x() - radius, anchor.z() - radius, side, side, feet, water);
    }

    public static int index(int radius, int dx, int dz) {
        return (dz + radius) * (2 * radius + 1) + dx + radius;
    }

    public int minX() {
        return minX;
    }

    public int minZ() {
        return minZ;
    }

    public int width() {
        return width;
    }

    public int depth() {
        return depth;
    }

    public boolean loaded() {
        for (int value : feet) {
            if (value == NO_GROUND) {
                return false;
            }
        }
        return true;
    }

    public boolean inside(int x, int z) {
        int dx = x - minX;
        int dz = z - minZ;
        return dx >= 0 && dz >= 0 && dx < width && dz < depth;
    }

    public int feet(int x, int z) {
        if (!inside(x, z)) {
            throw new IllegalArgumentException("Колонка " + x + "," + z + " вне снятого рельефа " + minX + "," + minZ
                    + " " + width + "×" + depth + ": план не выходит за снятый рельеф");
        }
        return feet[(z - minZ) * width + (x - minX)];
    }

    public boolean water(int x, int z) {
        return inside(x, z) && water.get((z - minZ) * width + (x - minX));
    }

    public boolean land(int x, int z) {
        return inside(x, z) && !water(x, z) && feet(x, z) != NO_GROUND;
    }
}
