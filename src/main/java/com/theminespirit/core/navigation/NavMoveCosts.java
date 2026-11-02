// lib: Из Baritone (github.com/cabaletta/baritone, pathing/movement/movements/*), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import static com.theminespirit.core.navigation.NavActionCosts.CENTER_AFTER_FALL_COST;
import static com.theminespirit.core.navigation.NavActionCosts.COST_INF;
import static com.theminespirit.core.navigation.NavActionCosts.FALL_N_BLOCKS_COST;
import static com.theminespirit.core.navigation.NavActionCosts.JUMP_ONE_BLOCK_COST;
import static com.theminespirit.core.navigation.NavActionCosts.LADDER_DOWN_ONE_COST;
import static com.theminespirit.core.navigation.NavActionCosts.LADDER_UP_ONE_COST;
import static com.theminespirit.core.navigation.NavActionCosts.WALK_OFF_BLOCK_COST;
import static com.theminespirit.core.navigation.NavActionCosts.WALK_ONE_BLOCK_COST;
import static com.theminespirit.core.navigation.NavActionCosts.WALK_ONE_IN_WATER_COST;

final class NavMoveCosts {
    enum Kind {
        TRAVERSE,
        ASCEND,
        DESCEND,
        DIAGONAL,
        PILLAR,
        DOWNWARD
    }

    private static final double SQRT_2 = Math.sqrt(2);

    private NavMoveCosts() {}

    static void fall(NavContext context, int x, int y, int z, int dx, int dz, NavMoveResult out) {
        int nx = x + dx;
        int nz = z + dz;
        int max = context.settings().maxFall();
        for (int depth = 2; depth <= context.settings().maxWaterFall(); depth++) {
            int feet = y - depth;
            if (!passableColumn(context, nx, y - 1, feet + 1, nz)) {
                return;
            }
            boolean landing = context.world().walkable(nx, feet - 1, nz);
            boolean water = context.world().water(nx, feet, nz);
            if (water) {
                max = context.settings().maxWaterFall();
            }
            if ((landing || water) && depth <= max) {
                out.x = nx;
                out.y = feet;
                out.z = nz;
                out.cost = WALK_OFF_BLOCK_COST + FALL_N_BLOCKS_COST[depth] + CENTER_AFTER_FALL_COST;
                return;
            }
            if (depth > max) {
                return;
            }
        }
    }

    static void traverse(NavContext context, int x, int y, int z, int dx, int dz, NavMoveResult out) {
        int nx = x + dx;
        int nz = z + dz;
        double clear = clear(context, nx, y, nz) + clear(context, nx, y + 1, nz);
        if (clear >= COST_INF) {
            return;
        }
        boolean water = context.world().water(nx, y, nz) || context.world().water(nx, y + 1, nz);
        double base = water ? WALK_ONE_IN_WATER_COST : WALK_ONE_BLOCK_COST;
        if (!context.world().walkable(nx, y - 1, nz) && !water) {
            if (!context.canPlace() || !context.world().passable(nx, y - 1, nz)) {
                return;
            }
            base += context.settings().placeCost();
        }
        set(out, nx, y, nz, base + clear);
    }

    static void ascend(NavContext context, int x, int y, int z, int dx, int dz, NavMoveResult out) {
        int nx = x + dx;
        int nz = z + dz;
        double clear = clear(context, x, y + 2, z) + clear(context, nx, y + 1, nz) + clear(context, nx, y + 2, nz);
        if (clear >= COST_INF) {
            return;
        }
        if (!context.world().walkable(nx, y, nz)) {
            if (!context.canPlace() || !context.world().passable(nx, y, nz)) {
                return;
            }
            clear += context.settings().placeCost();
        }
        set(
                out,
                nx,
                y + 1,
                nz,
                WALK_ONE_BLOCK_COST + JUMP_ONE_BLOCK_COST + context.settings().jumpPenalty() + clear);
    }

    static void descend(NavContext context, int x, int y, int z, int dx, int dz, NavMoveResult out) {
        int nx = x + dx;
        int nz = z + dz;
        double clear = clear(context, nx, y - 1, nz) + clear(context, nx, y, nz);
        if (clear >= COST_INF || !context.world().walkable(nx, y - 2, nz)) {
            return;
        }
        set(out, nx, y - 1, nz, WALK_OFF_BLOCK_COST + FALL_N_BLOCKS_COST[1] + CENTER_AFTER_FALL_COST + clear);
    }

    static void diagonal(NavContext context, int x, int y, int z, int dx, int dz, NavMoveResult out) {
        int nx = x + dx;
        int nz = z + dz;
        if (!context.world().passable(x + dx, y, z) || !context.world().passable(x, y, z + dz)) {
            return;
        }
        double clear = clear(context, nx, y, nz) + clear(context, nx, y + 1, nz);
        if (clear >= COST_INF || !context.world().walkable(nx, y - 1, nz)) {
            return;
        }
        boolean water = context.world().water(nx, y, nz) || context.world().water(nx, y + 1, nz);
        set(out, nx, y, nz, (water ? WALK_ONE_IN_WATER_COST : WALK_ONE_BLOCK_COST) * SQRT_2 + clear);
    }

    static void pillar(NavContext context, int x, int y, int z, NavMoveResult out) {
        if (context.world().climbable(x, y, z)) {
            set(out, x, y + 1, z, LADDER_UP_ONE_COST);
            return;
        }
        if (!context.canPlace()
                || !context.world().walkable(x, y - 1, z)
                || !context.world().passable(x, y + 1, z)) {
            return;
        }
        double clear = clear(context, x, y + 2, z);
        if (clear >= COST_INF) {
            return;
        }
        set(
                out,
                x,
                y + 1,
                z,
                JUMP_ONE_BLOCK_COST
                        + context.settings().placeCost()
                        + context.settings().jumpPenalty()
                        + clear);
    }

    static void downward(NavContext context, int x, int y, int z, NavMoveResult out) {
        if (context.world().climbable(x, y, z)) {
            set(out, x, y - 1, z, LADDER_DOWN_ONE_COST);
            return;
        }
        double breakTicks = context.world().breakTicks(x, y - 1, z);
        if (!context.settings().canBreak()
                || breakTicks >= COST_INF
                || !context.world().walkable(x, y - 2, z)) {
            return;
        }
        set(
                out,
                x,
                y - 1,
                z,
                FALL_N_BLOCKS_COST[1] + breakTicks + context.settings().breakPenalty());
    }

    private static double clear(NavContext context, int x, int y, int z) {
        if (context.world().water(x, y, z) || context.world().passable(x, y, z)) {
            return 0;
        }
        double ticks = context.world().breakTicks(x, y, z);
        return context.settings().canBreak() && ticks < COST_INF
                ? ticks + context.settings().breakPenalty()
                : COST_INF;
    }

    private static boolean passableColumn(NavContext context, int x, int fromY, int toY, int z) {
        for (int y = fromY; y >= toY; y--) {
            if (!context.world().passable(x, y, z) && !context.world().water(x, y, z)) {
                return false;
            }
        }
        return true;
    }

    private static void set(NavMoveResult out, int x, int y, int z, double cost) {
        out.x = x;
        out.y = y;
        out.z = z;
        out.cost = cost;
    }
}
