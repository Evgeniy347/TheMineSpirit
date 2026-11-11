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
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

public final class SurfaceEscapeSession {
    public static final int MAX_VISITS = 20_000;

    public record Result(boolean found, boolean primary, List<BlockCoord> path, int visits, long millis) {
        public Result {
            Objects.requireNonNull(path, "Путь не может быть null: пустой маршрут — путь из нуля клеток");
        }
    }

    private final RouteWorld world;
    private final Predicate<BlockCoord> goal;
    private final Predicate<BlockCoord> blocked;
    private final @Nullable Predicate<BlockCoord> fallback;
    private final LongSupplier clock;
    private final long started;
    private final Map<BlockCoord, BlockCoord> parent = new HashMap<>();
    private final Set<BlockCoord> seen = new HashSet<>();
    private final Queue<BlockCoord> queue;
    private @Nullable BlockCoord found;
    private @Nullable List<BlockCoord> fallbackPath;
    private @Nullable Result result;
    private boolean primary;
    private int visits;

    public SurfaceEscapeSession(
            BlockCoord start,
            RouteWorld world,
            Predicate<BlockCoord> goal,
            @Nullable BlockCoord preferred,
            Predicate<BlockCoord> blocked,
            @Nullable Predicate<BlockCoord> fallback) {
        this(start, world, goal, preferred, blocked, fallback, System::nanoTime);
    }

    SurfaceEscapeSession(
            BlockCoord start,
            RouteWorld world,
            Predicate<BlockCoord> goal,
            @Nullable BlockCoord preferred,
            Predicate<BlockCoord> blocked,
            @Nullable Predicate<BlockCoord> fallback,
            LongSupplier clock) {
        Objects.requireNonNull(start, "Старт не может быть null: без него путь к небу не начать");
        Objects.requireNonNull(world, "Мир не может быть null: без него не видно прохода");
        Objects.requireNonNull(goal, "Признак цели не может быть null: без него путь некуда вести");
        Objects.requireNonNull(blocked, "Запрет клеток не может быть null: пусто — запрета нет");
        this.world = world;
        this.goal = goal;
        this.blocked = blocked;
        this.fallback = fallback;
        this.clock = Objects.requireNonNull(clock, "Часы поиска не могут быть null: без них не видно бюджета");
        this.started = clock.getAsLong();
        this.queue = new PriorityQueue<>(order(preferred));
        if (!BodyMotion.fits(world, start)) {
            finish(Optional.empty(), false);
            return;
        }
        if (exit(world, start, goal)) {
            finish(Optional.of(List.of(start)), true);
            return;
        }
        if (fallback != null && exit(world, start, fallback)) {
            fallbackPath = List.of(start);
        }
        seen.add(start);
        queue.add(start);
    }

    private static Comparator<BlockCoord> order(@Nullable BlockCoord preferred) {
        return preferred == null
                ? Comparator.comparingInt(BlockCoord::y).reversed()
                : Comparator.comparingDouble((BlockCoord cell) -> distance(cell, preferred));
    }

    public @Nullable Result step(long budgetNanos) {
        if (budgetNanos < 0) {
            throw new IllegalArgumentException("Бюджет поиска " + budgetNanos + " нс отрицателен");
        }
        Result done = result;
        if (done != null) {
            return done;
        }
        long sliceStart = clock.getAsLong();
        while (!queue.isEmpty() && visits < MAX_VISITS) {
            if (clock.getAsLong() - sliceStart >= budgetNanos) {
                return null;
            }
            BlockCoord cell = queue.poll();
            visits++;
            for (BlockCoord next : BodyMotion.steps(world, cell)) {
                if (blocked.test(next) || !seen.add(next)) {
                    continue;
                }
                parent.put(next, cell);
                if (exit(world, next, goal)) {
                    found = next;
                    queue.clear();
                    break;
                }
                if (fallback != null && fallbackPath == null && exit(world, next, fallback)) {
                    fallbackPath = pathTo(next);
                }
                queue.add(next);
            }
        }
        if (found != null) {
            return finish(Optional.of(pathTo(found)), true);
        }
        return finish(Optional.ofNullable(fallbackPath), false);
    }

    private Result finish(Optional<List<BlockCoord>> path, boolean primaryHit) {
        primary = path.isPresent() && primaryHit;
        Result done = new Result(
                path.isPresent(),
                primary,
                path.orElse(List.of()),
                visits,
                Math.max(0, (clock.getAsLong() - started) / 1_000_000L));
        result = done;
        return done;
    }

    private List<BlockCoord> pathTo(BlockCoord end) {
        List<BlockCoord> path = new ArrayList<>();
        for (BlockCoord at = end; at != null; at = parent.get(at)) {
            path.add(at);
        }
        Collections.reverse(path);
        return List.copyOf(path);
    }

    static boolean exit(RouteWorld world, BlockCoord cell, Predicate<BlockCoord> sky) {
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
