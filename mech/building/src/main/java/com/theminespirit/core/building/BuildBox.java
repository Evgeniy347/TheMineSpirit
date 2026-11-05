package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record BuildBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public BuildBox {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException(
                    "Минимум коробки больше максимума: углы обязаны нормализоваться через of(...), а не конструктором");
        }
    }

    public static BuildBox of(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new BuildBox(
                Math.min(x1, x2),
                Math.min(y1, y2),
                Math.min(z1, z2),
                Math.max(x1, x2),
                Math.max(y1, y2),
                Math.max(z1, z2));
    }

    public static BuildBox cube(int x, int y, int z, int size) {
        if (size < 1) {
            throw new IllegalArgumentException("Сторона куба " + size + " меньше 1: копать нечего");
        }
        return of(x, y, z, x + size - 1, y + size - 1, z + size - 1);
    }

    public int sideX() {
        return maxX - minX + 1;
    }

    public int sideY() {
        return maxY - minY + 1;
    }

    public int sideZ() {
        return maxZ - minZ + 1;
    }

    public long volume() {
        return (long) sideX() * sideY() * sideZ();
    }

    public List<BlockCoord> cells() {
        List<BlockCoord> cells = new ArrayList<>((int) Math.min(volume(), Integer.MAX_VALUE));
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cells.add(new BlockCoord(x, y, z));
                }
            }
        }
        return List.copyOf(cells);
    }

    public List<BlockCoord> cellsBottomUp() {
        List<BlockCoord> cells = new ArrayList<>(cells());
        cells.sort(Comparator.comparingInt(BlockCoord::y)
                .thenComparingInt(BlockCoord::x)
                .thenComparingInt(BlockCoord::z));
        return List.copyOf(cells);
    }

    public boolean contains(BlockCoord coord) {
        Objects.requireNonNull(coord, "Координата не может быть null: без неё неизвестно, внутри ли она коробки");
        return coord.x() >= minX
                && coord.x() <= maxX
                && coord.y() >= minY
                && coord.y() <= maxY
                && coord.z() >= minZ
                && coord.z() <= maxZ;
    }

    public boolean exceeds(int maxVolume) {
        return volume() > maxVolume;
    }
}
