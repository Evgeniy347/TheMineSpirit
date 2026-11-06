// lib: Из Baritone (github.com/cabaletta/baritone, pathing/movement/Moves.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

public enum NavMoves {
    DOWNWARD(0, -1, 0, NavMoveCosts.Kind.DOWNWARD),
    PILLAR(0, 1, 0, NavMoveCosts.Kind.PILLAR),
    TRAVERSE_NORTH(0, 0, -1, NavMoveCosts.Kind.TRAVERSE),
    TRAVERSE_SOUTH(0, 0, 1, NavMoveCosts.Kind.TRAVERSE),
    TRAVERSE_EAST(1, 0, 0, NavMoveCosts.Kind.TRAVERSE),
    TRAVERSE_WEST(-1, 0, 0, NavMoveCosts.Kind.TRAVERSE),
    ASCEND_NORTH(0, 1, -1, NavMoveCosts.Kind.ASCEND),
    ASCEND_SOUTH(0, 1, 1, NavMoveCosts.Kind.ASCEND),
    ASCEND_EAST(1, 1, 0, NavMoveCosts.Kind.ASCEND),
    ASCEND_WEST(-1, 1, 0, NavMoveCosts.Kind.ASCEND),
    DESCEND_NORTH(0, -1, -1, NavMoveCosts.Kind.DESCEND),
    DESCEND_SOUTH(0, -1, 1, NavMoveCosts.Kind.DESCEND),
    DESCEND_EAST(1, -1, 0, NavMoveCosts.Kind.DESCEND),
    DESCEND_WEST(-1, -1, 0, NavMoveCosts.Kind.DESCEND),
    DIAGONAL_NORTHEAST(1, 0, -1, NavMoveCosts.Kind.DIAGONAL),
    DIAGONAL_NORTHWEST(-1, 0, -1, NavMoveCosts.Kind.DIAGONAL),
    DIAGONAL_SOUTHEAST(1, 0, 1, NavMoveCosts.Kind.DIAGONAL),
    DIAGONAL_SOUTHWEST(-1, 0, 1, NavMoveCosts.Kind.DIAGONAL);

    private final int dx;
    private final int dy;
    private final int dz;
    private final NavMoveCosts.Kind kind;

    NavMoves(int dx, int dy, int dz, NavMoveCosts.Kind kind) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.kind = kind;
    }

    public int dx() {
        return dx;
    }

    public int dy() {
        return dy;
    }

    public int dz() {
        return dz;
    }

    void apply(NavContext context, int x, int y, int z, NavMoveResult out) {
        switch (kind) {
            case TRAVERSE -> NavMoveCosts.traverse(context, x, y, z, dx, dz, out);
            case ASCEND -> NavMoveCosts.ascend(context, x, y, z, dx, dz, out);
            case DESCEND -> NavMoveCosts.descend(context, x, y, z, dx, dz, out);
            case DIAGONAL -> NavMoveCosts.diagonal(context, x, y, z, dx, dz, out);
            case PILLAR -> NavMoveCosts.pillar(context, x, y, z, out);
            case DOWNWARD -> NavMoveCosts.downward(context, x, y, z, out);
        }
    }
}
