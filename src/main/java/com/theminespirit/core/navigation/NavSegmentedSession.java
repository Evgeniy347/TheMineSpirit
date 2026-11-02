// lib: Из Baritone (github.com/cabaletta/baritone, behavior/PathingBehavior.java и path/PathExecutor.java), лицензия
// LGPL-3.0.
package com.theminespirit.core.navigation;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;
import org.jspecify.annotations.Nullable;

public final class NavSegmentedSession {
    private static final double BACKTRACK_FACTOR = 0.5;
    private static final long NANOS_PER_MILLI = 1_000_000L;

    private final NavContext context;
    private final NavGoal goal;
    private final int maxSegments;
    private final int minCutoff;
    private final double cutoffFactor;
    private final LongSupplier clock;
    private final long started;
    private final List<BlockCoord> positions = new ArrayList<>();
    private BlockCoord at;
    private NavFavoring favoring = NavFavoring.EMPTY;
    private @Nullable NavAStarSession current;
    private @Nullable NavSegmentedRoute result;
    private int segments;
    private int nodes;

    public NavSegmentedSession(
            NavContext context, BlockCoord start, NavGoal goal, int maxSegments, int minCutoff, double cutoffFactor) {
        this(context, start, goal, maxSegments, minCutoff, cutoffFactor, System::nanoTime);
    }

    NavSegmentedSession(
            NavContext context,
            BlockCoord start,
            NavGoal goal,
            int maxSegments,
            int minCutoff,
            double cutoffFactor,
            LongSupplier clock) {
        this.context = Objects.requireNonNull(context, "Контекст навигации не может быть null");
        this.goal = Objects.requireNonNull(goal, "Цель не может быть null");
        this.at = Objects.requireNonNull(start, "Старт не может быть null");
        if (maxSegments < 1) {
            throw new IllegalArgumentException("Число сегментов " + maxSegments + " меньше одного");
        }
        if (minCutoff < 2) {
            throw new IllegalArgumentException("Минимальная длина отрезка " + minCutoff + " меньше двух клеток");
        }
        this.maxSegments = maxSegments;
        this.minCutoff = minCutoff;
        this.cutoffFactor = cutoffFactor;
        this.clock = Objects.requireNonNull(clock, "Часы сегментов не могут быть null");
        this.started = clock.getAsLong();
        positions.add(at);
    }

    public @Nullable NavSegmentedRoute step(long budgetNanos) {
        if (budgetNanos < 0) {
            throw new IllegalArgumentException("Порция сегментов " + budgetNanos + " нс отрицательна");
        }
        NavSegmentedRoute done = result;
        if (done != null) {
            return done;
        }
        long sliceStart = clock.getAsLong();
        while (segments < maxSegments) {
            if (current == null) {
                current = new NavAStarSession(context, at.x(), at.y(), at.z(), goal, favoring, clock);
            }
            long remaining = budgetNanos - (clock.getAsLong() - sliceStart);
            if (remaining <= 0) {
                return null;
            }
            NavResult step = current.step(remaining);
            if (step == null) {
                return null;
            }
            nodes += step.nodes();
            NavPath path = step.path();
            if (path == null) {
                return finish(startStatus());
            }
            BlockCoord before = at;
            NavPath segment = NavPathCutoff.staticCutoff(
                    NavPathCutoff.atAvailable(path, context.world()), goal, minCutoff, cutoffFactor);
            if (segment.length() < 2) {
                return finish(startStatus());
            }
            append(segment);
            at = segment.end();
            segments++;
            current = null;
            if (step.found() || goal.isInGoal(at.x(), at.y(), at.z())) {
                return finish(NavSearchStatus.FOUND);
            }
            if (at.equals(before)) {
                return finish(NavSearchStatus.SEGMENT);
            }
            favoring = NavFavoring.of(segment, BACKTRACK_FACTOR);
        }
        return finish(segments == 0 ? NavSearchStatus.FAILURE : NavSearchStatus.SEGMENT);
    }

    public boolean done() {
        return result != null;
    }

    public int segments() {
        return segments;
    }

    public int nodes() {
        return nodes;
    }

    private NavSearchStatus startStatus() {
        return segments == 0 ? NavSearchStatus.FAILURE : NavSearchStatus.SEGMENT;
    }

    private NavSegmentedRoute finish(NavSearchStatus status) {
        NavSegmentedRoute finished = new NavSegmentedRoute(
                status, positions, segments, nodes, Math.max(0, (clock.getAsLong() - started) / NANOS_PER_MILLI), at);
        result = finished;
        return finished;
    }

    private void append(NavPath segment) {
        List<BlockCoord> cells = segment.positions();
        int from = cells.get(0).equals(at) ? 1 : 0;
        for (int i = from; i < cells.size(); i++) {
            if (!positions.get(positions.size() - 1).equals(cells.get(i))) {
                positions.add(cells.get(i));
            }
        }
    }
}
