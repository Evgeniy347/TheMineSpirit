// lib: Из Baritone (github.com/cabaletta/baritone, pathing/calc/AStarPathFinder.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import org.jspecify.annotations.Nullable;

public final class NavAStarSearch {
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
    private final Map<Long, NavNode> nodes = new HashMap<>();
    private final NavOpenSet open = new NavBinaryHeapOpenSet();
    private final NavNode[] bestSoFar = new NavNode[COEFFICIENTS.length];
    private final double[] bestHeuristicSoFar = new double[COEFFICIENTS.length];
    private boolean failing = true;
    private int emptyChunks;

    private NavAStarSearch(
            NavContext context,
            int startX,
            int startY,
            int startZ,
            NavGoal goal,
            NavFavoring favoring,
            LongSupplier clock) {
        this.context = context;
        this.goal = goal;
        this.favoring = favoring;
        this.startX = startX;
        this.startY = startY;
        this.startZ = startZ;
        this.clock = clock;
    }

    public static NavResult plan(NavContext context, int startX, int startY, int startZ, NavGoal goal) {
        return plan(context, startX, startY, startZ, goal, NavFavoring.EMPTY, System::nanoTime);
    }

    public static NavResult plan(
            NavContext context, int startX, int startY, int startZ, NavGoal goal, NavFavoring favoring) {
        return plan(context, startX, startY, startZ, goal, favoring, System::nanoTime);
    }

    static NavResult plan(NavContext context, int startX, int startY, int startZ, NavGoal goal, LongSupplier clock) {
        return plan(context, startX, startY, startZ, goal, NavFavoring.EMPTY, clock);
    }

    static NavResult plan(
            NavContext context,
            int startX,
            int startY,
            int startZ,
            NavGoal goal,
            NavFavoring favoring,
            LongSupplier clock) {
        return new NavAStarSearch(context, startX, startY, startZ, goal, favoring, clock).search();
    }

    private NavResult search() {
        long started = clock.getAsLong();
        long primaryNanos = millisToNanos(context.settings().primaryTimeoutMs());
        long failureNanos = millisToNanos(context.settings().failureTimeoutMs());
        NavNode start = nodeAt(startX, startY, startZ);
        start.cost = 0;
        start.combinedCost = start.estimatedCostToGoal;
        open.insert(start);
        for (int i = 0; i < COEFFICIENTS.length; i++) {
            bestHeuristicSoFar[i] = start.estimatedCostToGoal;
            bestSoFar[i] = start;
        }
        NavMoveResult move = new NavMoveResult();
        int considered = 0;
        while (!open.isEmpty()
                && considered < context.settings().maxNodes()
                && emptyChunks < context.settings().maxChunkBorderFetch()) {
            if ((considered & (TIME_CHECK_INTERVAL - 1)) == 0 && timedOut(started, primaryNanos, failureNanos)) {
                break;
            }
            NavNode current = open.removeLowest();
            considered++;
            if (goal.isInGoal(current.x, current.y, current.z)) {
                return result(NavSearchStatus.FOUND, path(current, true), considered, started);
            }
            expand(current, move);
        }
        NavPath best = bestPath();
        return best == null
                ? new NavResult(
                        NavSearchStatus.FAILURE,
                        null,
                        considered,
                        elapsed(started),
                        new BlockCoord(startX, startY, startZ))
                : result(NavSearchStatus.SEGMENT, best, considered, started);
    }

    private boolean timedOut(long started, long primaryNanos, long failureNanos) {
        long now = clock.getAsLong();
        long spent = now - started;
        return spent >= failureNanos || (!failing && spent >= primaryNanos);
    }

    private void expand(NavNode current, NavMoveResult move) {
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

    private void relax(NavNode current, NavMoveResult move) {
        if (move.cost >= NavActionCosts.COST_INF) {
            return;
        }
        if (move.y < context.world().minY() || move.y >= context.world().maxY()) {
            return;
        }
        if (crossesChunk(current.x, current.z, move.x, move.z)
                && !context.world().available(move.x, move.z)) {
            emptyChunks++;
            return;
        }
        NavNode neighbor = nodeAt(move.x, move.y, move.z);
        double tentative = current.cost + move.cost * favoring.factor(move.x, move.y, move.z);
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

    private NavResult result(NavSearchStatus status, NavPath path, int considered, long started) {
        return new NavResult(status, path, considered, elapsed(started), path.end());
    }

    private long elapsed(long started) {
        return Math.max(0, (clock.getAsLong() - started) / NANOS_PER_MILLI);
    }

    private static boolean crossesChunk(int x, int z, int nextX, int nextZ) {
        return (x >> 4) != (nextX >> 4) || (z >> 4) != (nextZ >> 4);
    }

    private static long millisToNanos(long millis) {
        return millis > Long.MAX_VALUE / NANOS_PER_MILLI ? Long.MAX_VALUE : millis * NANOS_PER_MILLI;
    }
}
