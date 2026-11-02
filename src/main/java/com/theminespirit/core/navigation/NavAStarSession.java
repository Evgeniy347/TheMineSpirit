// lib: Из Baritone (github.com/cabaletta/baritone, pathing/calc/AStarPathFinder.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;
import org.jspecify.annotations.Nullable;

public final class NavAStarSession {
    private static final double[] COEFFICIENTS = {1.5, 2, 2.5, 3, 4, 5, 10};
    private static final double MIN_DIST_PATH = 5;
    private static final double MIN_IMPROVEMENT = 0.01;
    private static final int TIME_CHECK_INTERVAL = 64;
    private static final int[][] FALL_DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final long NANOS_PER_MILLI = 1_000_000L;

    private final NavContext context;
    private final NavGoal goal;
    private final NavFavoring favoring;
    private final int startX;
    private final int startY;
    private final int startZ;
    private final LongSupplier clock;
    private final long started;
    private final Map<Long, NavNode> nodes = new HashMap<>();
    private final NavOpenSet open = new NavBinaryHeapOpenSet();
    private final NavNode[] bestSoFar = new NavNode[COEFFICIENTS.length];
    private final double[] bestHeuristicSoFar = new double[COEFFICIENTS.length];
    private final NavMoveResult move = new NavMoveResult();
    private int considered;
    private int emptyChunks;
    private boolean failing = true;
    private boolean terminated;

    public NavAStarSession(NavContext context, int startX, int startY, int startZ, NavGoal goal, NavFavoring favoring) {
        this(context, startX, startY, startZ, goal, favoring, System::nanoTime);
    }

    NavAStarSession(
            NavContext context,
            int startX,
            int startY,
            int startZ,
            NavGoal goal,
            NavFavoring favoring,
            LongSupplier clock) {
        this.context = Objects.requireNonNull(context, "Контекст навигации не может быть null");
        this.goal = Objects.requireNonNull(goal, "Цель не может быть null");
        this.favoring = Objects.requireNonNull(favoring, "Favoring не может быть null");
        this.startX = startX;
        this.startY = startY;
        this.startZ = startZ;
        this.clock = Objects.requireNonNull(clock, "Часы поиска не могут быть null");
        this.started = clock.getAsLong();
        NavNode start = nodeAt(startX, startY, startZ);
        start.cost = 0;
        start.combinedCost = start.estimatedCostToGoal;
        open.insert(start);
        for (int i = 0; i < COEFFICIENTS.length; i++) {
            bestHeuristicSoFar[i] = start.estimatedCostToGoal;
            bestSoFar[i] = start;
        }
    }

    public @Nullable NavResult step(long budgetNanos) {
        if (budgetNanos < 0) {
            throw new IllegalArgumentException("Порция поиска " + budgetNanos + " нс отрицательна");
        }
        if (terminated) {
            throw new IllegalStateException("Поиск уже завершён: повторный шаг не имеет смысла");
        }
        long sliceStart = clock.getAsLong();
        while (!open.isEmpty()
                && considered < context.settings().maxNodes()
                && emptyChunks < context.settings().maxChunkBorderFetch()) {
            if ((considered & (TIME_CHECK_INTERVAL - 1)) == 0 && timedOut()) {
                terminated = true;
                return finalResult();
            }
            if (clock.getAsLong() - sliceStart >= budgetNanos) {
                return null;
            }
            NavNode current = open.removeLowest();
            considered++;
            if (goal.isInGoal(current.x, current.y, current.z)) {
                terminated = true;
                return result(NavSearchStatus.FOUND, path(current, true));
            }
            expand(current);
        }
        terminated = true;
        return finalResult();
    }

    public boolean terminated() {
        return terminated;
    }

    public int considered() {
        return considered;
    }

    private boolean timedOut() {
        long spent = clock.getAsLong() - started;
        return spent >= millisToNanos(context.settings().failureTimeoutMs())
                || (!failing && spent >= millisToNanos(context.settings().primaryTimeoutMs()));
    }

    private void expand(NavNode current) {
        for (NavMoves option : NavMoves.values()) {
            move.reset(current.x, current.y, current.z);
            option.apply(context, current.x, current.y, current.z, move);
            relax(current, move);
        }
        for (int[] direction : FALL_DIRECTIONS) {
            move.reset(current.x, current.y, current.z);
            NavMoveCosts.fall(context, current.x, current.y, current.z, direction[0], direction[1], move);
            relax(current, move);
        }
    }

    private void relax(NavNode current, NavMoveResult candidate) {
        if (candidate.cost >= NavActionCosts.COST_INF) {
            return;
        }
        if (candidate.y < context.world().minY()
                || candidate.y >= context.world().maxY()) {
            return;
        }
        if (crossesChunk(current.x, current.z, candidate.x, candidate.z)
                && !context.world().available(candidate.x, candidate.z)) {
            emptyChunks++;
            return;
        }
        NavNode neighbor = nodeAt(candidate.x, candidate.y, candidate.z);
        double tentative = current.cost + candidate.cost * favoring.factor(candidate.x, candidate.y, candidate.z);
        if (neighbor.cost - tentative <= MIN_IMPROVEMENT) {
            return;
        }
        neighbor.previous = current;
        neighbor.cost = tentative;
        neighbor.combinedCost = tentative + neighbor.estimatedCostToGoal;
        if (neighbor.isOpen()) {
            open.update(neighbor);
        } else {
            open.insert(neighbor);
        }
        updateBest(neighbor);
    }

    private void updateBest(NavNode node) {
        for (int i = 0; i < COEFFICIENTS.length; i++) {
            double heuristic = node.estimatedCostToGoal + node.cost / COEFFICIENTS[i];
            if (bestHeuristicSoFar[i] - heuristic > MIN_IMPROVEMENT) {
                bestHeuristicSoFar[i] = heuristic;
                bestSoFar[i] = node;
                if (failing && distanceSq(node) > MIN_DIST_PATH * MIN_DIST_PATH) {
                    failing = false;
                }
            }
        }
    }

    private NavResult finalResult() {
        NavPath best = bestPath();
        return best == null
                ? new NavResult(
                        NavSearchStatus.FAILURE, null, considered, elapsed(), new BlockCoord(startX, startY, startZ))
                : result(NavSearchStatus.SEGMENT, best);
    }

    private @Nullable NavPath bestPath() {
        for (NavNode node : bestSoFar) {
            if (distanceSq(node) > MIN_DIST_PATH * MIN_DIST_PATH) {
                return path(node, false);
            }
        }
        return null;
    }

    private NavNode nodeAt(int x, int y, int z) {
        long key = NavNode.key(x, y, z);
        NavNode node = nodes.get(key);
        if (node == null) {
            node = new NavNode(x, y, z, goal);
            nodes.put(key, node);
        }
        return node;
    }

    private double distanceSq(NavNode node) {
        double dx = node.x - startX;
        double dy = node.y - startY;
        double dz = node.z - startZ;
        return dx * dx + dy * dy + dz * dz;
    }

    private NavPath path(NavNode end, boolean canReach) {
        List<BlockCoord> positions = new ArrayList<>();
        for (NavNode node = end; node != null; node = node.previous) {
            positions.add(new BlockCoord(node.x, node.y, node.z));
        }
        Collections.reverse(positions);
        return new NavPath(positions, end.cost, canReach);
    }

    private NavResult result(NavSearchStatus status, NavPath path) {
        return new NavResult(status, path, considered, elapsed(), path.end());
    }

    private long elapsed() {
        return Math.max(0, (clock.getAsLong() - started) / NANOS_PER_MILLI);
    }

    private static boolean crossesChunk(int x, int z, int nextX, int nextZ) {
        return (x >> 4) != (nextX >> 4) || (z >> 4) != (nextZ >> 4);
    }

    private static long millisToNanos(long millis) {
        return millis > Long.MAX_VALUE / NANOS_PER_MILLI ? Long.MAX_VALUE : millis * NANOS_PER_MILLI;
    }
}
