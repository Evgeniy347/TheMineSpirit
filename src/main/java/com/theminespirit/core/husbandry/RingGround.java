package com.theminespirit.core.husbandry;

import java.util.Arrays;
import java.util.Objects;

public final class RingGround {
    public record Cell(int x, int z) {
    }

    private final int minX;
    private final int minZ;
    private final int[][] heights;

    private RingGround(int minX, int minZ, int[][] heights) {
        this.minX = minX;
        this.minZ = minZ;
        this.heights = heights;
    }

    public static RingGround of(int minX, int minZ, int[][] heights) {
        Objects.requireNonNull(heights, "Карта высот не может быть null: без неё неизвестно, где земля");
        if (heights.length == 0) {
            throw new IllegalArgumentException("Карта высот пуста: кольцу нужна земля хотя бы под одной клеткой");
        }
        int width = heights[0].length;
        if (width == 0) {
            throw new IllegalArgumentException("Карта высот пуста: кольцу нужна земля хотя бы под одной клеткой");
        }
        for (int[] row : heights) {
            Objects.requireNonNull(row, "Строка карты высот не может быть null: в ней высоты клеток земли");
            if (row.length != width) {
                throw new IllegalArgumentException("Строки карты высот разной длины: карта обязана быть прямоугольником");
            }
        }
        int[][] copy = new int[heights.length][];
        for (int i = 0; i < heights.length; i++) {
            copy[i] = Arrays.copyOf(heights[i], width);
        }
        return new RingGround(minX, minZ, copy);
    }

    public static RingGround flat(int minX, int minZ, int sizeX, int sizeZ, int height) {
        if (sizeX < 1 || sizeZ < 1) {
            throw new IllegalArgumentException("Размер карты " + sizeX + "x" + sizeZ
                    + ": карта высот обязана покрывать хотя бы одну клетку");
        }
        int[][] heights = new int[sizeZ][sizeX];
        for (int[] row : heights) {
            Arrays.fill(row, height);
        }
        return new RingGround(minX, minZ, heights);
    }

    public boolean contains(int x, int z) {
        return x >= minX && x < minX + heights[0].length && z >= minZ && z < minZ + heights.length;
    }

    public boolean contains(Cell cell) {
        Objects.requireNonNull(cell, "Клетка не может быть null: без неё неизвестно, что проверять");
        return contains(cell.x(), cell.z());
    }

    public int heightAt(int x, int z) {
        if (!contains(x, z)) {
            throw new IllegalArgumentException("Клетка (" + x + ", " + z + ") вне карты высот: карта покрывает x in ["
                    + minX + ", " + (minX + heights[0].length - 1) + "], z in [" + minZ + ", "
                    + (minZ + heights.length - 1) + "]");
        }
        return heights[z - minZ][x - minX];
    }

    public int heightAt(Cell cell) {
        Objects.requireNonNull(cell, "Клетка не может быть null: без неё неизвестно, где мерить высоту");
        return heightAt(cell.x(), cell.z());
    }

    public int minX() {
        return minX;
    }

    public int minZ() {
        return minZ;
    }

    public int sizeX() {
        return heights[0].length;
    }

    public int sizeZ() {
        return heights.length;
    }
}
