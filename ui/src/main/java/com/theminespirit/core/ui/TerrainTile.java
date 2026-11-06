package com.theminespirit.core.ui;

import java.util.Arrays;

public final class TerrainTile {
    public static final int SIZE = 16;
    public static final int COLUMNS = SIZE * SIZE;
    public static final int UNKNOWN = -1;
    private static final int UNKNOWN_BYTE = 0xFF;
    private final int chunkX;
    private final int chunkZ;
    private final byte[] ids;

    public TerrainTile(int chunkX, int chunkZ, byte[] ids) {
        if (ids.length != COLUMNS) {
            throw new IllegalArgumentException("Тайл чанка " + chunkX + "," + chunkZ + " несёт " + ids.length
                    + " колонок, а надо " + COLUMNS + ": тайл — квадрат 16×16");
        }
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.ids = ids.clone();
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }

    public int id(int localX, int localZ) {
        if (localX < 0 || localZ < 0 || localX >= SIZE || localZ >= SIZE) {
            throw new IllegalArgumentException(
                    "Колонка " + localX + "," + localZ + " вне тайла 16×16: карта читает свои клетки");
        }
        int id = ids[localZ * SIZE + localX] & 0xFF;
        return id == UNKNOWN_BYTE ? UNKNOWN : id;
    }

    public boolean hasKnown() {
        for (byte id : ids) {
            if ((id & 0xFF) != UNKNOWN_BYTE) {
                return true;
            }
        }
        return false;
    }

    public byte[] ids() {
        return ids.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TerrainTile tile
                && chunkX == tile.chunkX
                && chunkZ == tile.chunkZ
                && Arrays.equals(ids, tile.ids);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * chunkX + chunkZ) + Arrays.hashCode(ids);
    }

    @Override
    public String toString() {
        return "TerrainTile[" + chunkX + "," + chunkZ + "]";
    }
}
