package com.theminespirit.core.movement;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.route.RouteCell;
import com.theminespirit.core.route.RouteWorld;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

public final class SurfaceEscape {
    public static final int MAX_VISITS = 20_000;
    private static final int MAX_FALL = 3;
    private static final int MAX_WATER_FALL = 32;
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private SurfaceEscape() {}

    public static Optional<List<BlockCoord>> path(BlockCoord start, RouteWorld world, Predicate<BlockCoord> sky) {
        return path(start, world, sky, null);
    }

    public static Optional<List<BlockCoord>> path(
            BlockCoord start, RouteWorld world, Predicate<BlockCoord> sky, @Nullable BlockCoord preferred) {
        Objects.requireNonNull(start, "Старт не может быть null: без него путь к небу не начать");
        Objects.requireNonNull(world, "Мир не может быть null: без него не видно прохода");
        Objects.requireNonNull(sky, "Признак неба не может быть null: без него путь некуда вести");
        if (sky.test(start)) {
            return Optional.of(List.of(start));
        }
        Map<BlockCoord, BlockCoord> parent = new HashMap<>();
        Set<BlockCoord> seen = new HashSet<>();
        Comparator<BlockCoord> order = preferred == null
                ? Comparator.comparingInt(BlockCoord::y).reversed()
                : Comparator.comparingDouble((BlockCoord cell) -> distance(cell, preferred));
        Queue<BlockCoord> queue = new PriorityQueue<>(order);
        seen.add(start);
        queue.add(start);
        BlockCoord found = null;
        int visits = 0;
        while (!queue.isEmpty() && visits++ < MAX_VISITS) {
            BlockCoord cell = queue.poll();
            for (BlockCoord next : moves(world, cell)) {
                if (!seen.add(next)) {
                    continue;
                }
                parent.put(next, cell);
                if (sky.test(next)) {
                    found = next;
                    queue.clear();
                    break;
                }
                queue.add(next);
            }
        }
        if (found == null) {
            return Optional.empty();
        }
        List<BlockCoord> path = new ArrayList<>();
        for (BlockCoord at = found; at != null; at = parent.get(at)) {
            path.add(at);
        }
        Collections.reverse(path);
        return Optional.of(List.copyOf(path));
    }

    private static List<BlockCoord> moves(RouteWorld world, BlockCoord from) {
        List<BlockCoord> moves = new ArrayList<>();
        int x = from.x();
        int y = from.y();
        int z = from.z();
        boolean wet = water(world, from);
        boolean standing = supported(world, from);
        for (int[] side : SIDES) {
            int nx = x + side[0];
            int nz = z + side[1];
            BlockCoord level = new BlockCoord(nx, y, nz);
            if (passable(world, level)) {
                if (supported(world, level) || climbable(world, level) || wet) {
                    moves.add(level);
                } else {
                    addFall(moves, world, nx, y, nz);
                }
            }
            if ((wet || standing || climbable(world, from)) && passable(world, new BlockCoord(nx, y + 1, nz))) {
                BlockCoord up = new BlockCoord(nx, y + 1, nz);
                if (supported(world, up) || climbable(world, up)) {
                    moves.add(up);
                }
            }
            BlockCoord down = new BlockCoord(nx, y - 1, nz);
            if (passable(world, down)) {
                if (supported(world, down) || climbable(world, down)) {
                    moves.add(down);
                } else {
                    addFall(moves, world, nx, y - 1, nz);
                }
            }
        }
        BlockCoord up = new BlockCoord(x, y + 1, z);
        if ((wet || standing || climbable(world, from))
                && passable(world, up)
                && (supported(world, up) || climbable(world, up))) {
            moves.add(up);
        }
        BlockCoord down = new BlockCoord(x, y - 1, z);
        if (passable(world, down)) {
            if (supported(world, down) || climbable(world, down)) {
                moves.add(down);
            } else {
                addFall(moves, world, x, y, z);
            }
        }
        return moves;
    }

    private static void addFall(List<BlockCoord> moves, RouteWorld world, int x, int y, int z) {
        for (int depth = 1; depth <= MAX_WATER_FALL; depth++) {
            BlockCoord landing = new BlockCoord(x, y - depth, z);
            if (!passable(world, landing)) {
                return;
            }
            if (water(world, landing)) {
                moves.add(landing);
                return;
            }
            if (solid(kind(world, new BlockCoord(x, landing.y() - 1, z))) || climbable(world, landing)) {
                if (depth <= MAX_FALL) {
                    moves.add(landing);
                }
                return;
            }
        }
    }

    private static double distance(BlockCoord from, BlockCoord to) {
        double dx = from.x() - to.x();
        double dy = from.y() - to.y();
        double dz = from.z() - to.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static boolean passable(RouteWorld world, BlockCoord cell) {
        return passable(kind(world, cell)) && passable(kind(world, new BlockCoord(cell.x(), cell.y() + 1, cell.z())));
    }

    private static boolean supported(RouteWorld world, BlockCoord cell) {
        BlockCoord below = new BlockCoord(cell.x(), cell.y() - 1, cell.z());
        return solid(kind(world, below)) || water(world, below) || water(world, cell) || climbable(world, cell);
    }

    private static boolean climbable(RouteWorld world, BlockCoord cell) {
        return kind(world, cell) == RouteCell.CLIMB;
    }

    private static boolean water(RouteWorld world, BlockCoord cell) {
        return kind(world, cell) == RouteCell.WATER;
    }

    private static int kind(RouteWorld world, BlockCoord cell) {
        return RouteCell.kind(world.cell(cell.x(), cell.y(), cell.z()));
    }

    private static boolean passable(int kind) {
        return kind == RouteCell.AIR || kind == RouteCell.CLIMB || kind == RouteCell.WATER;
    }

    private static boolean solid(int kind) {
        return kind == RouteCell.SOLID || kind == RouteCell.BLOCKED || kind == RouteCell.TALL;
    }
}
