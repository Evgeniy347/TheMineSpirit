// lib: Из Baritone (github.com/cabaletta/baritone, behavior/PathingBehavior.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class NavSegmentedPlanner {
    private static final double BACKTRACK_FACTOR = 0.5;

    private NavSegmentedPlanner() {}

    public static NavSegmentedRoute follow(
            NavContext context, BlockCoord start, NavGoal goal, int maxSegments, int minCutoff, double cutoffFactor) {
        Objects.requireNonNull(context, "Контекст навигации не может быть null");
        Objects.requireNonNull(start, "Старт не может быть null");
        Objects.requireNonNull(goal, "Цель не может быть null");
        if (maxSegments < 1) {
            throw new IllegalArgumentException("Число сегментов " + maxSegments + " меньше одного");
        }
        long started = System.nanoTime();
        BlockCoord at = start;
        List<BlockCoord> positions = new ArrayList<>();
        positions.add(at);
        NavFavoring favoring = NavFavoring.EMPTY;
        int segments = 0;
        int nodes = 0;
        boolean found = goal.isInGoal(start.x(), start.y(), start.z());
        while (!found && segments < maxSegments) {
            BlockCoord before = at;
            NavResult result = runSearch(context, at, goal, favoring);
            nodes += result.nodes();
            NavPath path = result.path();
            if (path == null) {
                break;
            }
            NavPath segment = NavPathCutoff.staticCutoff(
                    NavPathCutoff.atAvailable(path, context.world()), goal, minCutoff, cutoffFactor);
            if (segment.length() < 2) {
                break;
            }
            append(positions, segment, at);
            at = segment.end();
            segments++;
            found = result.found() || goal.isInGoal(at.x(), at.y(), at.z());
            if (at.equals(before)) {
                break;
            }
            favoring = NavFavoring.of(segment, BACKTRACK_FACTOR);
        }
        NavSearchStatus status =
                found ? NavSearchStatus.FOUND : segments == 0 ? NavSearchStatus.FAILURE : NavSearchStatus.SEGMENT;
        return new NavSegmentedRoute(
                status, positions, segments, nodes, (System.nanoTime() - started) / 1_000_000L, at);
    }

    private static NavResult runSearch(NavContext context, BlockCoord start, NavGoal goal, NavFavoring favoring) {
        NavAStarSession session = new NavAStarSession(context, start.x(), start.y(), start.z(), goal, favoring);
        NavResult result;
        do {
            result = session.step(Long.MAX_VALUE);
        } while (result == null);
        return result;
    }

    private static void append(List<BlockCoord> positions, NavPath segment, BlockCoord expectedStart) {
        List<BlockCoord> cells = segment.positions();
        int from = cells.get(0).equals(expectedStart) ? 1 : 0;
        for (int i = from; i < cells.size(); i++) {
            if (!positions.get(positions.size() - 1).equals(cells.get(i))) {
                positions.add(cells.get(i));
            }
        }
    }
}
