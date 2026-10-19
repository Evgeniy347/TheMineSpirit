package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Раскладка демо-постройки в мире: куда встанет схема, где сундук с материалами, где появляется дух и какой
 * объём площадки очистить и выровнять. Чистая функция от точки-якоря (клетка перед входом) и направления взгляда.
 *
 * <p>Площадка шире схемы на {@link BuildTuning#DEMO_PLATFORM_PADDING} блоков слева, справа, спереди, сзади и
 * вверх (вниз не расширяется): дух обходит дом по краю, а хлам вокруг не мешает. Сундук стоит на площадке в
 * стороне от входа — не вплотную к двери, не на линии подхода и не в клетке схемы.
 */
public record DemoPlacement(BuildRotation rotation, BlockCoord origin, BlockCoord chest, BlockCoord spawn,
        BuildBox platform, BuildBox floorBox, int sizeX, int sizeZ) {

    public DemoPlacement {
        Objects.requireNonNull(rotation, "Поворот не может быть null: без него постройка встанет не туда");
        Objects.requireNonNull(origin, "Начало постройки не может быть null: без него нечего строить");
        Objects.requireNonNull(chest, "Сундук не может быть null: без него дух не возьмёт материалы");
        Objects.requireNonNull(spawn, "Место появления духа не может быть null: без него дух встанет в схему");
        Objects.requireNonNull(platform, "Площадка не может быть null: без неё нечего чистить");
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
        int pad = BuildTuning.DEMO_PLATFORM_PADDING;
        BuildBox platform = BuildBox.of(originX - pad, anchorY, originZ - pad,
                originX + rotatedX - 1 + pad, anchorY + sizeY - 1 + pad, originZ + rotatedZ - 1 + pad);
        BuildBox floor = BuildBox.of(originX - pad, anchorY - 1, originZ - pad,
                originX + rotatedX - 1 + pad, anchorY - 1, originZ + rotatedZ - 1 + pad);
        BlockCoord chest = pickChest(anchorX, anchorY, anchorZ, lookDx, lookDz, originX, originZ,
                rotatedX, rotatedZ, pad);
        BlockCoord spawn = pickSpawn(anchorX, anchorY, anchorZ, originX, originZ, rotatedX, rotatedZ, pad, chest);
        return new DemoPlacement(rotation, new BlockCoord(originX, anchorY, originZ), chest, spawn, platform, floor,
                rotatedX, rotatedZ);
    }

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

    private static BlockCoord pickChest(int anchorX, int anchorY, int anchorZ, int lookDx, int lookDz,
            int originX, int originZ, int rotatedX, int rotatedZ, int pad) {
        int frontX = anchorX;
        int frontZ = anchorZ;
        int doorX = anchorX + lookDx;
        int doorZ = anchorZ + lookDz;
        int perpX = -lookDz == 0 && lookDx == 0 ? 1 : -lookDz;
        int perpZ = lookDx;
        int[][] preferred = {
                {frontX + perpX * 2, frontZ + perpZ * 2},
                {frontX - perpX * 2, frontZ - perpZ * 2},
                {doorX + perpX * 3, doorZ + perpZ * 3},
                {doorX - perpX * 3, doorZ - perpZ * 3},
        };
        for (int[] cell : preferred) {
            if (chestFits(cell[0], cell[1], frontX, frontZ, doorX, doorZ, lookDx, lookDz, originX, originZ,
                    rotatedX, rotatedZ, pad)) {
                return new BlockCoord(cell[0], anchorY, cell[1]);
            }
        }
        for (BlockCoord cell : platformByDistance(anchorX, anchorZ, originX, originZ, rotatedX, rotatedZ, pad)) {
            if (chestFits(cell.x(), cell.z(), frontX, frontZ, doorX, doorZ, lookDx, lookDz, originX, originZ,
                    rotatedX, rotatedZ, pad)) {
                return new BlockCoord(cell.x(), anchorY, cell.z());
            }
        }
        throw new IllegalArgumentException("На площадке нет места под сундук: вход " + frontX + "," + frontZ
                + " и подход к нему заняли весь запас вокруг постройки");
    }

    private static BlockCoord pickSpawn(int anchorX, int anchorY, int anchorZ, int originX, int originZ,
            int rotatedX, int rotatedZ, int pad, BlockCoord chest) {
        if (spawnFits(anchorX, anchorZ, originX, originZ, rotatedX, rotatedZ, pad)
                && (anchorX != chest.x() || anchorZ != chest.z())) {
            return new BlockCoord(anchorX, anchorY, anchorZ);
        }
        for (BlockCoord cell : platformByDistance(anchorX, anchorZ, originX, originZ, rotatedX, rotatedZ, pad)) {
            if (spawnFits(cell.x(), cell.z(), originX, originZ, rotatedX, rotatedZ, pad)
                    && (cell.x() != chest.x() || cell.z() != chest.z())) {
                return new BlockCoord(cell.x(), anchorY, cell.z());
            }
        }
        throw new IllegalArgumentException("На площадке нет свободной клетки для духа: сундук и схема заняли всё");
    }

    private static boolean chestFits(int x, int z, int frontX, int frontZ, int doorX, int doorZ,
            int lookDx, int lookDz, int originX, int originZ, int rotatedX, int rotatedZ, int pad) {
        return onPlatform(x, z, originX, originZ, rotatedX, rotatedZ, pad)
                && !inFootprint(x, z, originX, originZ, rotatedX, rotatedZ)
                && chebyshev(x, z, frontX, frontZ) >= 2
                && chebyshev(x, z, doorX, doorZ) >= 2
                && !onApproach(x, z, doorX, doorZ, lookDx, lookDz);
    }

    private static boolean spawnFits(int x, int z, int originX, int originZ, int rotatedX, int rotatedZ, int pad) {
        return onPlatform(x, z, originX, originZ, rotatedX, rotatedZ, pad)
                && !inFootprint(x, z, originX, originZ, rotatedX, rotatedZ);
    }

    private static boolean onPlatform(int x, int z, int originX, int originZ, int rotatedX, int rotatedZ, int pad) {
        return x >= originX - pad && x <= originX + rotatedX - 1 + pad
                && z >= originZ - pad && z <= originZ + rotatedZ - 1 + pad;
    }

    private static boolean inFootprint(int x, int z, int originX, int originZ, int rotatedX, int rotatedZ) {
        return x >= originX && x <= originX + rotatedX - 1
                && z >= originZ && z <= originZ + rotatedZ - 1;
    }

    private static boolean onApproach(int x, int z, int doorX, int doorZ, int lookDx, int lookDz) {
        if (lookDx == 0 && lookDz == 0) {
            return false;
        }
        if (lookDx != 0) {
            return z == doorZ && (x - doorX) * lookDx < 0;
        }
        return x == doorX && (z - doorZ) * lookDz < 0;
    }

    private static int chebyshev(int x, int z, int otherX, int otherZ) {
        return Math.max(Math.abs(x - otherX), Math.abs(z - otherZ));
    }

    private static List<BlockCoord> platformByDistance(int anchorX, int anchorZ, int originX, int originZ,
            int rotatedX, int rotatedZ, int pad) {
        List<BlockCoord> cells = new ArrayList<>();
        for (int x = originX - pad; x <= originX + rotatedX - 1 + pad; x++) {
            for (int z = originZ - pad; z <= originZ + rotatedZ - 1 + pad; z++) {
                cells.add(new BlockCoord(x, 0, z));
            }
        }
        cells.sort(Comparator.comparingInt((BlockCoord cell) -> chebyshev(cell.x(), cell.z(), anchorX, anchorZ))
                .thenComparingInt(cell -> chebyshev(cell.x(), cell.z(), originX, originZ))
                .thenComparingInt(BlockCoord::x).thenComparingInt(BlockCoord::z));
        return cells;
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
