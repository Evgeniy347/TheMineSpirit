package com.theminespirit.core.movement;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.route.RouteCell;
import com.theminespirit.core.route.RouteWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class BodyMotion {
    public static final int HEIGHT_CELLS = 2;
    public static final int STEP_UP = 1;
    public static final int MAX_FALL = 3;
    public static final int MAX_WATER_FALL = 32;

    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private BodyMotion() {}

    public static boolean fits(RouteWorld world, BlockCoord feet) {
        Objects.requireNonNull(world, "Мир не может быть null: без него не видно клеток тела");
        Objects.requireNonNull(feet, "Клетка ног не может быть null: от неё считается тело");
        return passable(world, feet) && passable(world, above(feet));
    }

    public static boolean standable(RouteWorld world, BlockCoord feet) {
        return fits(world, feet) && supported(world, feet);
    }

    public static List<BlockCoord> blocking(RouteWorld world, BlockCoord feet) {
        Objects.requireNonNull(world, "Мир не может быть null: без него не видно клеток тела");
        Objects.requireNonNull(feet, "Клетка ног не может быть null: от неё считается тело");
        List<BlockCoord> blocked = new ArrayList<>(2);
        if (!passable(world, feet)) {
            blocked.add(feet);
        }
        BlockCoord head = above(feet);
        if (!passable(world, head)) {
            blocked.add(head);
        }
        return List.copyOf(blocked);
    }

    public static boolean supported(RouteWorld world, BlockCoord feet) {
        return solid(world, below(feet)) || water(world, feet) || water(world, below(feet)) || climbable(world, feet);
    }

    public static boolean canMove(RouteWorld world, BlockCoord from, BlockCoord to) {
        return canMove(world, from, to, MAX_FALL);
    }

    public static boolean canMove(RouteWorld world, BlockCoord from, BlockCoord to, int maxFall) {
        Objects.requireNonNull(world, "Мир не может быть null: без него не видно перехода");
        Objects.requireNonNull(from, "Исходная клетка не может быть null: без неё перехода нет");
        Objects.requireNonNull(to, "Целевая клетка не может быть null: без неё перехода нет");
        if (maxFall < 0) {
            throw new IllegalArgumentException("Предел падения " + maxFall + " отрицателен: падение вниз не бывает");
        }
        if (!fits(world, from)) {
            return false;
        }
        int dx = to.x() - from.x();
        int dz = to.z() - from.z();
        int dy = to.y() - from.y();
        int horizontal = Math.abs(dx) + Math.abs(dz);
        if (horizontal == 0) {
            if (dy == STEP_UP) {
                return (water(world, from) || climbable(world, from)) && fits(world, to) && supported(world, to);
            }
            return dy < 0 && drop(world, from, to, maxFall);
        }
        if (horizontal != 1) {
            return false;
        }
        if (dy == 0) {
            return fits(world, to) && supported(world, to);
        }
        if (dy == STEP_UP) {
            return (water(world, from) || climbable(world, from) || (standable(world, from) && headroom(world, from)))
                    && fits(world, to)
                    && supported(world, to);
        }
        return dy < 0 && drop(world, from, to, maxFall);
    }

    public static List<BlockCoord> steps(RouteWorld world, BlockCoord from) {
        return steps(world, from, MAX_FALL);
    }

    public static List<BlockCoord> steps(RouteWorld world, BlockCoord from, int maxFall) {
        Objects.requireNonNull(world, "Мир не может быть null: без него не видно шагов");
        Objects.requireNonNull(from, "Клетка ног не может быть null: от неё считаются шаги");
        if (!fits(world, from)) {
            return List.of();
        }
        List<BlockCoord> moves = new ArrayList<>(8);
        boolean wet = water(world, from);
        boolean ladder = climbable(world, from);
        for (int[] side : SIDES) {
            int nx = from.x() + side[0];
            int nz = from.z() + side[1];
            BlockCoord level = new BlockCoord(nx, from.y(), nz);
            if (canMove(world, from, level, maxFall)) {
                moves.add(level);
            }
            BlockCoord up = new BlockCoord(nx, from.y() + STEP_UP, nz);
            if (canMove(world, from, up, maxFall)) {
                moves.add(up);
            }
            addDrop(world, from, nx, nz, maxFall, moves);
        }
        if (wet || ladder) {
            BlockCoord up = new BlockCoord(from.x(), from.y() + STEP_UP, from.z());
            if (canMove(world, from, up, maxFall)) {
                moves.add(up);
            }
            addDrop(world, from, from.x(), from.z(), maxFall, moves);
        }
        return List.copyOf(moves);
    }

    public static boolean headroom(RouteWorld world, BlockCoord feet) {
        return passable(world, new BlockCoord(feet.x(), feet.y() + HEIGHT_CELLS, feet.z()));
    }

    public static boolean passable(RouteWorld world, BlockCoord cell) {
        int kind = kind(world, cell);
        return kind == RouteCell.AIR || kind == RouteCell.CLIMB || kind == RouteCell.WATER;
    }

    public static boolean solid(RouteWorld world, BlockCoord cell) {
        int kind = kind(world, cell);
        return kind == RouteCell.SOLID || kind == RouteCell.BLOCKED || kind == RouteCell.TALL;
    }

    public static boolean water(RouteWorld world, BlockCoord cell) {
        return kind(world, cell) == RouteCell.WATER;
    }

    public static boolean climbable(RouteWorld world, BlockCoord cell) {
        return kind(world, cell) == RouteCell.CLIMB;
    }

    public static BlockCoord above(BlockCoord cell) {
        return new BlockCoord(cell.x(), cell.y() + 1, cell.z());
    }

    public static BlockCoord below(BlockCoord cell) {
        return new BlockCoord(cell.x(), cell.y() - 1, cell.z());
    }

    private static void addDrop(
            RouteWorld world, BlockCoord from, int nx, int nz, int maxFall, List<BlockCoord> moves) {
        for (int depth = 1; depth <= MAX_WATER_FALL; depth++) {
            BlockCoord landing = new BlockCoord(nx, from.y() - depth, nz);
            if (!passable(world, landing)) {
                return;
            }
            if (canMove(world, from, landing, maxFall)) {
                moves.add(landing);
                return;
            }
            if (solid(world, below(landing)) || climbable(world, landing)) {
                return;
            }
        }
    }

    private static boolean drop(RouteWorld world, BlockCoord from, BlockCoord to, int maxFall) {
        int depth = from.y() - to.y();
        if (depth < 1) {
            return false;
        }
        for (int step = 0; step <= depth; step++) {
            BlockCoord cell = new BlockCoord(to.x(), from.y() - step, to.z());
            if (!passable(world, cell)) {
                return false;
            }
        }
        if (water(world, to) || water(world, below(to))) {
            return depth <= MAX_WATER_FALL;
        }
        if (climbable(world, to)) {
            return depth <= Math.max(maxFall, MAX_FALL);
        }
        return solid(world, below(to)) && depth <= maxFall;
    }

    private static int kind(RouteWorld world, BlockCoord cell) {
        return RouteCell.kind(world.cell(cell.x(), cell.y(), cell.z()));
    }
}
