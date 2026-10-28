package com.theminespirit.core.town;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Objects;

public record StageSite(
        int stage,
        BlockCoord anchor,
        Placement hut,
        Placement yard,
        Placement mine,
        MinePlot minePlot,
        Placement orchard,
        List<BlockCoord> orchardSaplings,
        ColumnMask level,
        ColumnMask enclosure,
        FenceLine ring) {
    public static final int BUILDING_HEIGHT = 7;
    public static final int CAVE_DEPTH = 3;
    public static final int CAVE_MARGIN = 12;
    public static final int HOUSE_WIDTH = 5;
    public static final int HOUSE_DEPTH = 6;
    public static final int HOUSE_SHIFT_X = 1;
    public static final int HOUSE_SHIFT_Z = 3;
    public static final int YARD_STATION_ROW = 1;
    public static final int MINE_SHIFT = 1;
    public static final int MINE_WIDTH = 3;
    public static final int ORCHARD_TREE_HEIGHT = 16;
    public static final int MINE_SHALLOW = 12;

    public StageSite {
        Objects.requireNonNull(anchor, "Якорь этапа не может быть null: от него высота площадок");
        Objects.requireNonNull(hut, "Место хижины не может быть null: этап 1 без хижины не собрать");
        Objects.requireNonNull(yard, "Место двора не может быть null");
        Objects.requireNonNull(mine, "Место спуска в шахту не может быть null");
        Objects.requireNonNull(
                minePlot,
                "Прямоугольник шахты не может быть null: обе вкладки карты и стройка "
                        + "берут спуск из плана участка");
        Objects.requireNonNull(orchard, "Место сада не может быть null");
        orchardSaplings = List.copyOf(Objects.requireNonNull(
                orchardSaplings, "Места деревьев сада не могут быть null: пусто — пустой список"));
        Objects.requireNonNull(level, "Площадки выравнивания не могут быть null");
        Objects.requireNonNull(enclosure, "Огороженная область не может быть null");
        Objects.requireNonNull(ring, "Ограда этапа не может быть null");
    }

    public BlockCoord houseOrigin() {
        return new BlockCoord(hut.x() + HOUSE_SHIFT_X, anchor.y(), hut.z() + HOUSE_SHIFT_Z);
    }

    public boolean houseColumn(int x, int z) {
        BlockCoord house = houseOrigin();
        return x >= house.x() && x < house.x() + HOUSE_WIDTH && z >= house.z() && z < house.z() + HOUSE_DEPTH;
    }

    public BlockCoord workYard() {
        return new BlockCoord(yard.centerX(), anchor.y(), yard.z() + YARD_STATION_ROW + 1);
    }

    public boolean yardColumn(int x, int z) {
        return yard.covers(x, z);
    }

    public BlockCoord mineEntrance() {
        return minePlot.origin();
    }

    public boolean mineColumn(int x, int z) {
        int left = mine.x() + MINE_SHIFT;
        return x >= left && x < left + MINE_WIDTH && z >= mine.z() && z < mine.z() + mine.depth();
    }

    public boolean minePassage(int x, int z) {
        return mineColumn(x, z) && x == mine.x() + MINE_SHIFT + 1;
    }

    public BlockCoord orchardOrigin() {
        return new BlockCoord(orchard.x(), anchor.y(), orchard.z());
    }

    public boolean orchardTree(int x, int y, int z) {
        if (y < anchor.y() - 1 || y > anchor.y() + ORCHARD_TREE_HEIGHT) {
            return false;
        }
        for (BlockCoord spot : orchardSaplings) {
            if (spot.x() == x && spot.z() == z) {
                return true;
            }
        }
        return false;
    }

    public boolean ringPost(int x, int z) {
        return ring.posts().contains(new ColumnMask.Cell(x, z));
    }

    public boolean levelColumn(int x, int z) {
        return level.contains(x, z) && !mineColumn(x, z);
    }

    public boolean inside(int x, int z) {
        return enclosure.contains(x, z);
    }

    public boolean planCell(int x, int y, int z) {
        if (mineColumn(x, z) || ringPost(x, z)) {
            return true;
        }
        if (y < anchor.y() - 1 || y > anchor.y() + BUILDING_HEIGHT) {
            return false;
        }
        return hut.covers(x, z) || yard.covers(x, z) || orchard.covers(x, z) || mine.covers(x, z);
    }

    public boolean planned(int x, int y, int z) {
        return planCell(x, y, z) || orchard.covers(x, z) || (levelColumn(x, z) && y < anchor.y());
    }

    public boolean gatherForbidden(BlockCoord target, int depthBelowSurface) {
        Objects.requireNonNull(target, "Цель добычи не может быть null: проверяется её клетка");
        if (mineColumn(target.x(), target.z())) {
            return target.y() >= anchor.y() - MINE_SHALLOW;
        }
        if (inside(target.x(), target.z())) {
            return true;
        }
        return depthBelowSurface > CAVE_DEPTH && near(target.x(), target.z());
    }

    public boolean near(int x, int z) {
        return x >= enclosure.minX() - CAVE_MARGIN
                && x < enclosure.minX() + enclosure.width() + CAVE_MARGIN
                && z >= enclosure.minZ() - CAVE_MARGIN
                && z < enclosure.minZ() + enclosure.depth() + CAVE_MARGIN;
    }

    public boolean fillStandAllowed(BlockCoord stand, boolean sky) {
        Objects.requireNonNull(stand, "Стоянка не может быть null: проверяется её клетка");
        return sky || !levelColumn(stand.x(), stand.z());
    }
}
