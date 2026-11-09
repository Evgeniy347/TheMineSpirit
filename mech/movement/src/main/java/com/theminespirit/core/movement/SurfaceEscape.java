package com.theminespirit.core.movement;

import com.theminespirit.core.observation.BlockCoord;
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

    private SurfaceEscape() {}

    public static Optional<List<BlockCoord>> path(BlockCoord start, RouteWorld world, Predicate<BlockCoord> sky) {
        return path(start, world, sky, null);
    }

    public static Optional<List<BlockCoord>> path(
            BlockCoord start, RouteWorld world, Predicate<BlockCoord> sky, @Nullable BlockCoord preferred) {
        return path(start, world, sky, preferred, cell -> false);
    }

    public static Optional<List<BlockCoord>> path(
            BlockCoord start,
            RouteWorld world,
            Predicate<BlockCoord> sky,
            @Nullable BlockCoord preferred,
            Predicate<BlockCoord> blocked) {
        Objects.requireNonNull(start, "Старт не может быть null: без него путь к небу не начать");
        Objects.requireNonNull(world, "Мир не может быть null: без него не видно прохода");
        Objects.requireNonNull(sky, "Признак неба не может быть null: без него путь некуда вести");
        Objects.requireNonNull(blocked, "Запрет клеток не может быть null: пусто — запрета нет");
        if (!BodyMotion.fits(world, start)) {
            return Optional.empty();
        }
        if (exit(world, start, sky)) {
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
                if (blocked.test(next) || !seen.add(next)) {
                    continue;
                }
                parent.put(next, cell);
                if (exit(world, next, sky)) {
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
        return BodyMotion.steps(world, from);
    }

    private static boolean exit(RouteWorld world, BlockCoord cell, Predicate<BlockCoord> sky) {
        if (!BodyMotion.standable(world, cell) || !sky.test(cell)) {
            return false;
        }
        for (int[] side : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            BlockCoord near = new BlockCoord(cell.x() + side[0], cell.y(), cell.z() + side[1]);
            if ((sky.test(near) && BodyMotion.standable(world, near))
                    || BodyMotion.fits(world, near)
                    || BodyMotion.canMove(world, cell, near)) {
                return true;
            }
        }
        return false;
    }

    private static double distance(BlockCoord from, BlockCoord to) {
        double dx = from.x() - to.x();
        double dy = from.y() - to.y();
        double dz = from.z() - to.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
