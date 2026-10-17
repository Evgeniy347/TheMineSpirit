package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;

/**
 * Раскладка демо-постройки в мире: куда встанет схема, где сундук с материалами и какие объёмы
 * очистить и выровнять. Чистая функция от точки-якоря (блок, куда смотрит игрок) и направления взгляда.
 *
 * <p>Без явного поворота постройка разворачивается входом к игроку: внешняя нормаль клетки входа
 * (по метаданным пресета) поворачивается в сторону, противоположную взгляду. Сундук стоит в клетке-якоре,
 * вход — на одну клетку дальше от игрока, за входом — площадка под постройку.
 */
public record DemoPlacement(BuildRotation rotation, BlockCoord origin, BlockCoord chest,
        BuildBox clearBox, BuildBox floorBox, int sizeX, int sizeZ) {

    public DemoPlacement {
        Objects.requireNonNull(rotation, "Поворот не может быть null: без него постройка встанет не туда");
        Objects.requireNonNull(origin, "Начало постройки не может быть null: без него нечего строить");
        Objects.requireNonNull(chest, "Сундук не может быть null: без него дух не возьмёт материалы");
        Objects.requireNonNull(clearBox, "Объём очистки не может быть null: без него площадка не расчистится");
        Objects.requireNonNull(floorBox, "Объём пола не может быть null: без него фундамент не выровняется");
    }

    public static DemoPlacement compute(int anchorX, int anchorY, int anchorZ, int lookDx, int lookDz,
            int entryX, int entryZ, int sizeX, int sizeY, int sizeZ, BuildRotation explicit) {
        if (sizeX < 1 || sizeY < 1 || sizeZ < 1) {
            throw new IllegalArgumentException("Размер постройки " + sizeX + "×" + sizeY + "×" + sizeZ
                    + ": стороны обязаны быть хотя бы 1");
        }
        BuildRotation rotation = explicit == null
                ? chooseRotation(entryX, entryZ, sizeX, sizeZ, lookDx, lookDz)
                : explicit;
        int rotatedX = rotation.rotatedSizeX(sizeX, sizeZ);
        int rotatedZ = rotation.rotatedSizeZ(sizeX, sizeZ);
        BuildRotation.Cell entry = rotation.rotate(entryX, entryZ, sizeX, sizeZ);
        int originX = anchorX + lookDx - entry.x();
        int originZ = anchorZ + lookDz - entry.z();
        BuildBox clear = BuildBox.of(originX - 1, anchorY - 1, originZ - 1,
                originX + rotatedX, anchorY + sizeY, originZ + rotatedZ);
        BuildBox floor = BuildBox.of(originX - 1, anchorY - 1, originZ - 1,
                originX + rotatedX, anchorY - 1, originZ + rotatedZ);
        return new DemoPlacement(rotation, new BlockCoord(originX, anchorY, originZ),
                new BlockCoord(anchorX, anchorY, anchorZ), clear, floor, rotatedX, rotatedZ);
    }

    /** Поворот, при котором вход смотрит на игрока; при входе в глубине схемы поворот не меняется. */
    public static BuildRotation chooseRotation(int entryX, int entryZ, int sizeX, int sizeZ,
            int lookDx, int lookDz) {
        if (lookDx == 0 && lookDz == 0) {
            return BuildRotation.NONE;
        }
        int[] normal = entryNormal(entryX, entryZ, sizeX, sizeZ);
        if (normal == null) {
            return BuildRotation.NONE;
        }
        int wantedDx = -lookDx;
        int wantedDz = -lookDz;
        for (int quarter = 0; quarter < 4; quarter++) {
            BuildRotation.DirectionVector rotated = BuildRotation.rotateDirection(normal[0], normal[1], quarter);
            if (rotated.equalsVector(wantedDx, wantedDz)) {
                return BuildRotation.ofQuarterTurns(quarter);
            }
        }
        return BuildRotation.NONE;
    }

    private static int[] entryNormal(int entryX, int entryZ, int sizeX, int sizeZ) {
        if (entryZ == 0) {
            return new int[] {0, -1};
        }
        if (entryZ == sizeZ - 1) {
            return new int[] {0, 1};
        }
        if (entryX == 0) {
            return new int[] {-1, 0};
        }
        if (entryX == sizeX - 1) {
            return new int[] {1, 0};
        }
        return null;
    }
}
