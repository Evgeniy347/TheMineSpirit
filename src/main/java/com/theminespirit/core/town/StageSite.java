package com.theminespirit.core.town;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record StageSite(
        int stage,
        BlockCoord anchor,
        Placement hut,
        Placement yard,
        Placement mine,
        Placement grove,
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
    public static final int PALISADE_GATE = 3;
    public static final int YARD_CHEST_ROW = 3;
    public static final int YARD_STATION_ROW = 1;
    public static final int MINE_SHIFT = 1;
    public static final int MINE_WIDTH = 3;
    public static final int GROVE_INSET = 2;

    public StageSite {
        Objects.requireNonNull(anchor, "Якорь этапа не может быть null: от него высота площадок");
        Objects.requireNonNull(hut, "Место хижины не может быть null: этап 1 без хижины не собрать");
        Objects.requireNonNull(yard, "Место двора не может быть null");
        Objects.requireNonNull(mine, "Место спуска в шахту не может быть null");
        Objects.requireNonNull(grove, "Место рощи не может быть null");
        Objects.requireNonNull(level, "Площадки выравнивания не могут быть null");
        Objects.requireNonNull(enclosure, "Огороженная область не может быть null");
        Objects.requireNonNull(ring, "Ограда этапа не может быть null");
    }

    public BlockCoord houseOrigin() {
        return new BlockCoord(hut.x() + HOUSE_SHIFT_X, anchor.y(), hut.z() + HOUSE_SHIFT_Z);
    }

    public BlockCoord palisadeOrigin() {
        return new BlockCoord(hut.x(), anchor.y(), hut.z());
    }

    public boolean houseColumn(int x, int z) {
        BlockCoord house = houseOrigin();
        return x >= house.x() && x < house.x() + HOUSE_WIDTH && z >= house.z() && z < house.z() + HOUSE_DEPTH;
    }

    public BlockCoord workYard() {
        return new BlockCoord(yard.centerX(), anchor.y(), yard.z() + YARD_STATION_ROW + 1);
    }

    public BlockCoord craftingTable() {
        return new BlockCoord(yard.x() + 1, anchor.y(), yard.z() + YARD_STATION_ROW);
    }

    public BlockCoord furnace() {
        return new BlockCoord(yard.x() + yard.width() - 2, anchor.y(), yard.z() + YARD_STATION_ROW);
    }

    public List<List<BlockCoord>> chestSlots() {
        List<List<BlockCoord>> slots = new ArrayList<>();
        for (int x = yard.x(); x + 1 < yard.x() + yard.width(); x += 2) {
            int z = yard.z() + YARD_CHEST_ROW;
            slots.add(List.of(new BlockCoord(x, anchor.y(), z), new BlockCoord(x + 1, anchor.y(), z)));
        }
        return List.copyOf(slots);
    }

    public boolean yardColumn(int x, int z) {
        return yard.covers(x, z);
    }

    public BlockCoord mineEntrance() {
        return new BlockCoord(mine.x() + MINE_SHIFT, anchor.y() - 1, mine.z() + 1);
    }

    public boolean mineColumn(int x, int z) {
        int left = mine.x() + MINE_SHIFT;
        return x >= left && x < left + MINE_WIDTH && z >= mine.z() && z < mine.z() + mine.depth();
    }

    public boolean minePassage(int x, int z) {
        return mineColumn(x, z) && x == mine.x() + MINE_SHIFT + 1;
    }

    public List<BlockCoord> saplings() {
        int near = GROVE_INSET;
        int far = grove.width() - 1 - GROVE_INSET;
        return List.of(
                new BlockCoord(grove.x() + near, anchor.y(), grove.z() + near),
                new BlockCoord(grove.x() + far, anchor.y(), grove.z() + near),
                new BlockCoord(grove.x() + near, anchor.y(), grove.z() + far),
                new BlockCoord(grove.x() + far, anchor.y(), grove.z() + far));
    }

    public List<ColumnMask.Cell> torchPosts() {
        List<ColumnMask.Cell> posts = new ArrayList<>();
        int size = ring.posts().size();
        for (int gate : ring.gates()) {
            for (int side : new int[] {-1, 1}) {
                int at = Math.floorMod(gate + side, size);
                if (!ring.isGate(at) && !posts.contains(ring.posts().get(at))) {
                    posts.add(ring.posts().get(at));
                }
            }
        }
        return List.copyOf(posts);
    }

    public List<BlockCoord> groundTorches() {
        return List.of(
                new BlockCoord(hut.x() + PALISADE_GATE - 1, anchor.y() + 1, hut.z()),
                new BlockCoord(hut.x() + PALISADE_GATE + 1, anchor.y() + 1, hut.z()),
                new BlockCoord(yard.x() + yard.width() - 1, anchor.y(), yard.z() + YARD_STATION_ROW),
                new BlockCoord(mine.x(), anchor.y(), mine.z()),
                new BlockCoord(mine.x() + mine.width() - 1, anchor.y(), mine.z()));
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
        return hut.covers(x, z) || yard.covers(x, z) || grove.covers(x, z) || mine.covers(x, z);
    }

    public boolean planned(int x, int y, int z) {
        return planCell(x, y, z) || grove.covers(x, z) || (levelColumn(x, z) && y < anchor.y());
    }

    public boolean gatherForbidden(BlockCoord target, int depthBelowSurface) {
        Objects.requireNonNull(target, "Цель добычи не может быть null: проверяется её клетка");
        if (mineColumn(target.x(), target.z())) {
            return false;
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
