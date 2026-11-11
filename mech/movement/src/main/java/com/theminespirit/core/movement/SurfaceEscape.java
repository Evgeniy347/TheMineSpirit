package com.theminespirit.core.movement;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.route.RouteWorld;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

public final class SurfaceEscape {
    private static final long UNLIMITED_NANOS = Long.MAX_VALUE;

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
        return drain(new SurfaceEscapeSession(start, world, sky, preferred, blocked, null));
    }

    public static SurfaceEscapeSession toHome(
            BlockCoord start,
            RouteWorld world,
            BlockCoord target,
            int radius,
            Predicate<BlockCoord> blocked,
            Predicate<BlockCoord> sky) {
        Objects.requireNonNull(target, "Цель дома не может быть null: без неё нет домашнего выхода");
        Objects.requireNonNull(sky, "Признак неба не может быть null: без него нет запасной цели");
        Predicate<BlockCoord> home = cell -> SurfaceGoal.home(cell, target, radius);
        return new SurfaceEscapeSession(start, world, home, target, blocked, sky);
    }

    private static Optional<List<BlockCoord>> drain(SurfaceEscapeSession session) {
        SurfaceEscapeSession.Result result;
        do {
            result = session.step(UNLIMITED_NANOS);
        } while (result == null);
        return result.found() ? Optional.of(result.path()) : Optional.empty();
    }
}
