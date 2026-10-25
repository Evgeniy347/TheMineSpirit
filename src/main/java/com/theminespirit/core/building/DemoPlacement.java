package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record DemoPlacement(
        BuildRotation rotation,
        BlockCoord origin,
        BlockCoord chest,
        @Nullable BlockCoord chestPartner,
        @Nullable BlockCoord sign,
        Look chestFacing,
        BlockCoord spawn,
        BuildBox platform,
        BuildBox floorBox,
        int sizeX,
        int sizeZ) {

    public DemoPlacement {
        Objects.requireNonNull(rotation, "Поворот не может быть null: без него постройка встанет не туда");
        Objects.requireNonNull(origin, "Начало постройки не может быть null: без него нечего строить");
        Objects.requireNonNull(chest, "Сундук не может быть null: без него дух не возьмёт материалы");
        Objects.requireNonNull(chestFacing, "Взгляд сундука не может быть null: без него не собрать двойной сундук");
        Objects.requireNonNull(spawn, "Место появления духа не может быть null: без него дух встанет в схему");
        Objects.requireNonNull(platform, "Площадка не может быть null: без неё нечего чистить");
        Objects.requireNonNull(floorBox, "Объём пола не может быть null: без него фундамент не выровняется");
    }

    public static DemoPlacement compute(
            Anchor anchor, Look look, int entryX, int entryZ, Size size, @Nullable BuildRotation explicit) {
        int[] normal = entryNormal(entryX, entryZ, size.x(), size.z());
        Door door =
                normal == null ? new Door(entryX, entryZ, 0, 0, 0) : new Door(entryX, entryZ, normal[0], normal[1], 0);
        return compute(anchor, look, door, size, explicit);
    }

    public static DemoPlacement compute(
            Anchor anchor, Look look, Door door, Size size, @Nullable BuildRotation explicit) {
        int entryX = door.x();
        int entryZ = door.z();
        if (size.x() < 1 || size.y() < 1 || size.z() < 1) {
            throw new IllegalArgumentException("Размер постройки " + size.x() + "×" + size.y() + "×" + size.z()
                    + ": стороны обязаны быть хотя бы 1");
        }
        BuildRotation rotation = explicit == null ? chooseRotation(door.normalDx(), door.normalDz(), look) : explicit;
        int rotatedX = rotation.rotatedSizeX(size.x(), size.z());
        int rotatedZ = rotation.rotatedSizeZ(size.x(), size.z());
        BuildRotation.Cell entry = rotation.rotate(entryX, entryZ, size.x(), size.z());
        int originX = anchor.x() + look.dx() * (1 + door.porch()) - entry.x();
        int originZ = anchor.z() + look.dz() * (1 + door.porch()) - entry.z();
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
        ChestGroup group = pickChest(anchor, look, frame);
        BlockCoord spawn = pickSpawn(anchor, frame, group);
        return new DemoPlacement(
                rotation,
                new BlockCoord(originX, anchor.y(), originZ),
                group.chest(),
                group.partner(),
                group.sign(),
                group.facing(),
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
        return chooseRotation(normal[0], normal[1], new Look(lookDx, lookDz));
    }

    private static BuildRotation chooseRotation(int normalDx, int normalDz, Look look) {
        int lookDx = look.dx();
        int lookDz = look.dz();
        if ((lookDx == 0 && lookDz == 0) || (normalDx == 0 && normalDz == 0)) {
            return BuildRotation.NONE;
        }
        int[] normal = {normalDx, normalDz};
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

    private static ChestGroup pickChest(Anchor anchor, Look look, Frame frame) {
        int frontX = anchor.x();
        int frontZ = anchor.z();
        int doorX = anchor.x() + look.dx();
        int doorZ = anchor.z() + look.dz();
        Entrance entrance = new Entrance(frontX, frontZ, doorX, doorZ, look);
        int perpX = -look.dz() == 0 && look.dx() == 0 ? 1 : -look.dz();
        int perpZ = look.dx();
        Look facing = new Look(-perpZ, perpX);
        int[][] preferred = {
            {frontX + perpX * 2, frontZ + perpZ * 2},
            {frontX - perpX * 4, frontZ - perpZ * 4},
            {doorX + perpX * 3, doorZ + perpZ * 3},
            {doorX - perpX * 5, doorZ - perpZ * 5},
        };
        for (int width = 3; width >= 1; width--) {
            for (int[] cell : preferred) {
                if (groupFits(cell[0], cell[1], perpX, perpZ, width, entrance, frame)) {
                    return group(cell[0], cell[1], anchor.y(), perpX, perpZ, width, facing);
                }
            }
            for (BlockCoord cell : platformByDistance(frame)) {
                if (groupFits(cell.x(), cell.z(), perpX, perpZ, width, entrance, frame)) {
                    return group(cell.x(), cell.z(), anchor.y(), perpX, perpZ, width, facing);
                }
            }
        }
        throw new IllegalArgumentException("На площадке нет места под сундук: вход " + frontX + "," + frontZ
                + " и подход к нему заняли весь запас вокруг постройки");
    }

    private static boolean groupFits(int x, int z, int perpX, int perpZ, int width, Entrance entrance, Frame frame) {
        for (int i = 0; i < width; i++) {
            if (!chestFits(x + perpX * i, z + perpZ * i, entrance, frame)) {
                return false;
            }
        }
        return true;
    }

    private static ChestGroup group(int x, int z, int y, int perpX, int perpZ, int width, Look facing) {
        BlockCoord chest = new BlockCoord(x, y, z);
        BlockCoord partner = width == 3 ? new BlockCoord(x + perpX, y, z + perpZ) : null;
        BlockCoord sign = width >= 2 ? new BlockCoord(x + perpX * (width - 1), y, z + perpZ * (width - 1)) : null;
        return new ChestGroup(chest, partner, sign, facing);
    }

    private static BlockCoord pickSpawn(Anchor anchor, Frame frame, ChestGroup group) {
        if (spawnFits(anchor.x(), anchor.z(), frame) && !group.covers(anchor.x(), anchor.z())) {
            return new BlockCoord(anchor.x(), anchor.y(), anchor.z());
        }
        for (BlockCoord cell : platformByDistance(frame)) {
            if (spawnFits(cell.x(), cell.z(), frame) && !group.covers(cell.x(), cell.z())) {
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

    private static int @Nullable [] entryNormal(int entryX, int entryZ, int sizeX, int sizeZ) {
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

    public record Door(int x, int z, int normalDx, int normalDz, int porch) {
        public static Door of(Preset preset) {
            BlockCoord entry = preset.entry();
            SchematicBody.Normal normal = SchematicBody.of(preset.schematic(), entry)
                    .outwardNormal(entry)
                    .orElse(new SchematicBody.Normal(0, 0));
            int porch = 0;
            if (normal.dz() < 0) {
                porch = entry.z();
            } else if (normal.dz() > 0) {
                porch = preset.sizeZ() - 1 - entry.z();
            } else if (normal.dx() < 0) {
                porch = entry.x();
            } else if (normal.dx() > 0) {
                porch = preset.sizeX() - 1 - entry.x();
            }
            return new Door(entry.x(), entry.z(), normal.dx(), normal.dz(), porch);
        }
    }

    private record Frame(int anchorX, int anchorZ, int originX, int originZ, int rotatedX, int rotatedZ, int pad) {}

    private record ChestGroup(
            BlockCoord chest,
            @Nullable BlockCoord partner,
            @Nullable BlockCoord sign,
            Look facing) {
        boolean covers(int x, int z) {
            return same(chest, x, z) || same(partner, x, z) || same(sign, x, z);
        }

        private static boolean same(@Nullable BlockCoord cell, int x, int z) {
            return cell != null && cell.x() == x && cell.z() == z;
        }
    }

    private record Entrance(int frontX, int frontZ, int doorX, int doorZ, Look look) {}
}
