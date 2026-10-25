package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Objects;

public final class SettlementLayout {
    public static final int PLOT = 23;
    public static final int HALF = PLOT / 2;
    public static final int LEVEL_MARGIN = 2;
    public static final int LEVEL_HALF = HALF + LEVEL_MARGIN;
    public static final int CAVE_DEPTH = 3;
    public static final int YARD_RADIUS = 2;
    public static final int BUILDING_HEIGHT = 7;
    public static final int HOUSE_WIDTH = 5;
    public static final int HOUSE_DEPTH = 6;
    public static final int MINE_OFFSET = 6;
    public static final int YARD_OFFSET = -6;
    public static final int LIGHT_X = 3;
    public static final int LIGHT_NORTH = -6;
    public static final int LIGHT_SOUTH = 5;

    private SettlementLayout() {}

    public static BlockCoord fenceOrigin(BlockCoord anchor) {
        Objects.requireNonNull(anchor, "Якорь участка не может быть null: от него раскладывается поселение");
        return new BlockCoord(anchor.x() - HALF, anchor.y(), anchor.z() - HALF);
    }

    public static BlockCoord gate(BlockCoord anchor) {
        Objects.requireNonNull(anchor, "Якорь участка не может быть null: от него ставится калитка");
        return new BlockCoord(anchor.x(), anchor.y(), anchor.z() - HALF);
    }

    public static BlockCoord houseOrigin(BlockCoord anchor) {
        Objects.requireNonNull(anchor, "Якорь участка не может быть null: от него ставится дом");
        return new BlockCoord(anchor.x() - HOUSE_WIDTH / 2, anchor.y(), anchor.z() - HOUSE_DEPTH / 2);
    }

    public static BlockCoord mineEntrance(BlockCoord anchor) {
        Objects.requireNonNull(anchor, "Якорь участка не может быть null: от него ставится вход в шахту");
        return new BlockCoord(anchor.x() + MINE_OFFSET, anchor.y() - 1, anchor.z());
    }

    public static boolean mineColumn(BlockCoord anchor, int x, int z) {
        Objects.requireNonNull(anchor, "Якорь участка не может быть null: от него ставится вход в шахту");
        BlockCoord entrance = mineEntrance(anchor);
        return x >= entrance.x() && x <= entrance.x() + 2 && z >= entrance.z() - 1 && z <= anchor.z() + LEVEL_HALF;
    }

    public static boolean minePassage(BlockCoord anchor, int x, int z) {
        return mineColumn(anchor, x, z) && x == mineEntrance(anchor).x() + 1;
    }

    public static boolean gatherForbidden(BlockCoord anchor, BlockCoord target, int depthBelowSurface) {
        Objects.requireNonNull(anchor, "Якорь участка не может быть null: от него считается запретная зона добычи");
        Objects.requireNonNull(target, "Цель добычи не может быть null: проверяется её клетка");
        if (mineColumn(anchor, target.x(), target.z())) {
            return false;
        }
        int dx = Math.abs(target.x() - anchor.x());
        int dz = Math.abs(target.z() - anchor.z());
        if (dx <= LEVEL_HALF && dz <= LEVEL_HALF) {
            return true;
        }
        return depthBelowSurface > CAVE_DEPTH && dx <= 2 * PLOT && dz <= 2 * PLOT;
    }

    public static boolean planCell(BlockCoord anchor, int x, int y, int z) {
        Objects.requireNonNull(anchor, "Якорь участка не может быть null: клетки плана считаются от него");
        if (mineColumn(anchor, x, z)) {
            return true;
        }
        if (y < anchor.y() - 1 || y > anchor.y() + BUILDING_HEIGHT) {
            return false;
        }
        int dx = x - anchor.x();
        int dz = z - anchor.z();
        boolean fence =
                (Math.abs(dx) == HALF && Math.abs(dz) <= HALF) || (Math.abs(dz) == HALF && Math.abs(dx) <= HALF);
        boolean hut = houseColumn(anchor, x, z);
        BlockCoord yard = workYard(anchor);
        boolean yardCell = Math.abs(x - yard.x()) <= YARD_RADIUS && Math.abs(z - yard.z()) <= YARD_RADIUS;
        boolean torch = lighting(anchor).stream().anyMatch(cell -> cell.x() == x && cell.z() == z);
        return fence || hut || yardCell || torch;
    }

    public static boolean fillStandAllowed(BlockCoord anchor, BlockCoord stand, boolean sky) {
        Objects.requireNonNull(anchor, "Якорь участка не может быть null: от него считается участок");
        Objects.requireNonNull(stand, "Стоянка не может быть null: проверяется её клетка");
        boolean onPlot = Math.abs(stand.x() - anchor.x()) <= LEVEL_HALF
                && Math.abs(stand.z() - anchor.z()) <= LEVEL_HALF
                && !mineColumn(anchor, stand.x(), stand.z());
        return sky || !onPlot;
    }

    public static boolean houseColumn(BlockCoord anchor, int x, int z) {
        BlockCoord house = houseOrigin(anchor);
        return x >= house.x() && x < house.x() + HOUSE_WIDTH && z >= house.z() && z < house.z() + HOUSE_DEPTH;
    }

    public static BlockCoord workYard(BlockCoord anchor) {
        Objects.requireNonNull(anchor, "Якорь участка не может быть null: от него ставится рабочий двор");
        return new BlockCoord(anchor.x() + YARD_OFFSET, anchor.y(), anchor.z());
    }

    public static List<BlockCoord> lighting(BlockCoord anchor) {
        Objects.requireNonNull(anchor, "Якорь участка не может быть null: от него ставятся факелы");
        return List.of(
                new BlockCoord(anchor.x() - LIGHT_X, anchor.y(), anchor.z() + LIGHT_NORTH),
                new BlockCoord(anchor.x() + LIGHT_X, anchor.y(), anchor.z() + LIGHT_NORTH),
                new BlockCoord(anchor.x() - LIGHT_X, anchor.y(), anchor.z() + LIGHT_SOUTH),
                new BlockCoord(anchor.x() + LIGHT_X, anchor.y(), anchor.z() + LIGHT_SOUTH));
    }
}
