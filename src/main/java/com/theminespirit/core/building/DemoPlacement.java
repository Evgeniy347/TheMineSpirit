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
public record DemoPlacement(
        BuildRotation rotation,
        BlockCoord origin,
        BlockCoord chest,
        BlockCoord spawn,
        BuildBox platform,
        BuildBox floorBox,
        int sizeX,
        int sizeZ) {

    public DemoPlacement {
        Objects.requireNonNull(rotation, "Поворот не может быть null: без него постройка встанет не туда");
        Objects.requireNonNull(origin, "Начало постройки не может быть null: без него нечего строить");
        Objects.requireNonNull(chest, "Сундук не может быть null: без него дух не возьмёт материалы");
        Objects.requireNonNull(spawn, "Место появления духа не может быть null: без него дух встанет в схему");
        Objects.requireNonNull(platform, "Площадка не может быть null: без неё нечего чистить");
        Objects.requireNonNull(floorBox, "Объём пола не может быть null: без него фундамент не выровняется");
    }

    public static DemoPlacement compute(
            Anchor anchor, Look look, int entryX, int entryZ, Size size, BuildRotation explicit) {
        if (size.x() < 1 || size.y() < 1 || size.z() < 1) {
            throw new IllegalArgumentException("Размер постройки " + size.x() + "×" + size.y() + "×" + size.z()
                    + ": стороны обязаны быть хотя бы 1");
        }
        BuildRotation rotation =
                explicit == null ? chooseRotation(entryX, entryZ, size.x(), size.z(), look.dx(), look.dz()) : explicit;
        int rotatedX = rotation.rotatedSizeX(size.x(), size.z());
        int rotatedZ = rotation.rotatedSizeZ(size.x(), size.z());
        BuildRotation.Cell entry = rotation.rotate(entryX, entryZ, size.x(), size.z());
        int originX = anchor.x() + look.dx() - entry.x();
        int originZ = anchor.z() + look.dz() - entry.z();
        int pad = BuildTuning.DEMO_PLATFORM_PADDING;
        Frame frame = new Frame(anchor.x(), anchor.z(), originX, originZ, rotatedX, rotatedZ, pad);
        BuildBox platform = BuildBox.of(
                originX - pad,
                anchor.y(),
                originZ - pad,
                originX + rotatedX - 1 + pad,
                anchor.y() + size.y() - 1 + pad,
                originZ + rotatedZ - 1 + pad);
        BuildBox floor = BuildBox.of(
                originX - pad,
                anchor.y() - 1,
                originZ - pad,
                originX + rotatedX - 1 + pad,
                anchor.y() - 1,
                originZ + rotatedZ - 1 + pad);
        BlockCoord chest = pickChest(anchor, look, frame);
        BlockCoord spawn = pickSpawn(anchor, frame, chest);
        return new DemoPlacement(
                rotation,
                new BlockCoord(originX, anchor.y(), originZ),
                chest,
                spawn,
                platform,
                floor,
                rotatedX,
                rotatedZ);
    }

    public static BuildRotation chooseRotation(int entryX, int entryZ, int sizeX, int sizeZ, int lookDx, int lookDz) {
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

    private static BlockCoord pickChest(Anchor anchor, Look look, Frame frame) {
        int frontX = anchor.x();
        int frontZ = anchor.z();
        int doorX = anchor.x() + look.dx();
        int doorZ = anchor.z() + look.dz();
        Entrance entrance = new Entrance(frontX, frontZ, doorX, doorZ, look);
        int perpX = -look.dz() == 0 && look.dx() == 0 ? 1 : -look.dz();
        int perpZ = look.dx();
        int[][] preferred = {
            {frontX + perpX * 2, frontZ + perpZ * 2},
            {frontX - perpX * 2, frontZ - perpZ * 2},
            {doorX + perpX * 3, doorZ + perpZ * 3},
            {doorX - perpX * 3, doorZ - perpZ * 3},
        };
        for (int[] cell : preferred) {
            if (chestFits(cell[0], cell[1], entrance, frame)) {
                return new BlockCoord(cell[0], anchor.y(), cell[1]);
            }
        }
        for (BlockCoord cell : platformByDistance(frame)) {
            if (chestFits(cell.x(), cell.z(), entrance, frame)) {
                return new BlockCoord(cell.x(), anchor.y(), cell.z());
            }
        }
        throw new IllegalArgumentException("На площадке нет места под сундук: вход " + frontX + "," + frontZ
                + " и подход к нему заняли весь запас вокруг постройки");
    }

    private static BlockCoord pickSpawn(Anchor anchor, Frame frame, BlockCoord chest) {
        if (spawnFits(anchor.x(), anchor.z(), frame) && (anchor.x() != chest.x() || anchor.z() != chest.z())) {
            return new BlockCoord(anchor.x(), anchor.y(), anchor.z());
        }
        for (BlockCoord cell : platformByDistance(frame)) {
            if (spawnFits(cell.x(), cell.z(), frame) && (cell.x() != chest.x() || cell.z() != chest.z())) {
                return new BlockCoord(cell.x(), anchor.y(), cell.z());
            }
        }
        throw new IllegalArgumentException("На площадке нет свободной клетки для духа: сундук и схема заняли всё");
    }

    private static boolean chestFits(int x, int z, Entrance entrance, Frame frame) {
        return onPlatform(x, z, frame)
                && !inFootprint(x, z, frame)
                && chebyshev(x, z, entrance.frontX(), entrance.frontZ()) >= 2
                && chebyshev(x, z, entrance.doorX(), entrance.doorZ()) >= 2
                && !onApproach(x, z, entrance);
    }

    private static boolean spawnFits(int x, int z, Frame frame) {
        return onPlatform(x, z, frame) && !inFootprint(x, z, frame);
    }

    private static boolean onPlatform(int x, int z, Frame frame) {
        return x >= frame.originX() - frame.pad()
                && x <= frame.originX() + frame.rotatedX() - 1 + frame.pad()
                && z >= frame.originZ() - frame.pad()
                && z <= frame.originZ() + frame.rotatedZ() - 1 + frame.pad();
    }

    private static boolean inFootprint(int x, int z, Frame frame) {
        return x >= frame.originX()
                && x <= frame.originX() + frame.rotatedX() - 1
                && z >= frame.originZ()
                && z <= frame.originZ() + frame.rotatedZ() - 1;
    }

    private static boolean onApproach(int x, int z, Entrance entrance) {
        Look look = entrance.look();
        if (look.dx() == 0 && look.dz() == 0) {
            return false;
        }
        if (look.dx() != 0) {
            return z == entrance.doorZ() && (x - entrance.doorX()) * look.dx() < 0;
        }
        return x == entrance.doorX() && (z - entrance.doorZ()) * look.dz() < 0;
    }

    private static int chebyshev(int x, int z, int otherX, int otherZ) {
        return Math.max(Math.abs(x - otherX), Math.abs(z - otherZ));
    }

    private static List<BlockCoord> platformByDistance(Frame frame) {
        List<BlockCoord> cells = new ArrayList<>();
        for (int x = frame.originX() - frame.pad(); x <= frame.originX() + frame.rotatedX() - 1 + frame.pad(); x++) {
            for (int z = frame.originZ() - frame.pad();
                    z <= frame.originZ() + frame.rotatedZ() - 1 + frame.pad();
                    z++) {
                cells.add(new BlockCoord(x, 0, z));
            }
        }
        cells.sort(Comparator.comparingInt(
                        (BlockCoord cell) -> chebyshev(cell.x(), cell.z(), frame.anchorX(), frame.anchorZ()))
                .thenComparingInt(cell -> chebyshev(cell.x(), cell.z(), frame.originX(), frame.originZ()))
                .thenComparingInt(BlockCoord::x)
                .thenComparingInt(BlockCoord::z));
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

    public record Anchor(int x, int y, int z) {}

    public record Look(int dx, int dz) {}

    public record Size(int x, int y, int z) {}

    private record Frame(int anchorX, int anchorZ, int originX, int originZ, int rotatedX, int rotatedZ, int pad) {}

    private record Entrance(int frontX, int frontZ, int doorX, int doorZ, Look look) {}
}
