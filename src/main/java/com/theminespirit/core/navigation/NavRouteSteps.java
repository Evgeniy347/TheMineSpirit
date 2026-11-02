// lib: Из Baritone (github.com/cabaletta/baritone, pathing/calc/Path.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.route.Route;
import com.theminespirit.core.route.RouteStep;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class NavRouteSteps {
    private static final double TICKS_PER_STEP = 10.0;

    private NavRouteSteps() {}

    public static Route toRoute(NavSegmentedRoute segmented, NavWorld world, BlockCoord target) {
        Objects.requireNonNull(segmented, "Сегменты не могут быть null: нечего превращать в шаги");
        Objects.requireNonNull(world, "Мир не может быть null: по нему выбираются действия");
        Objects.requireNonNull(target, "Цель не может быть null");
        List<BlockCoord> positions = segmented.positions();
        List<RouteStep> steps = new ArrayList<>();
        for (int i = 1; i < positions.size(); i++) {
            if (!append(world, steps, positions.get(i))) {
                return new Route(
                        Route.Status.NONE,
                        List.of(),
                        0,
                        positions.get(positions.size() - 1),
                        segmented.nodes(),
                        0,
                        0,
                        List.of());
            }
        }
        Route.Status status = switch (segmented.status()) {
            case FOUND -> Route.Status.FOUND;
            case SEGMENT -> Route.Status.PARTIAL;
            case FAILURE -> Route.Status.NONE;
        };
        return new Route(
                status,
                steps,
                steps.size() * TICKS_PER_STEP,
                positions.get(positions.size() - 1),
                segmented.nodes(),
                0,
                0,
                List.of());
    }

    private static boolean append(NavWorld world, List<RouteStep> steps, BlockCoord next) {
        boolean water = world.water(next.x(), next.y(), next.z()) || world.water(next.x(), next.y() + 1, next.z());
        if (!water && !world.passable(next.x(), next.y(), next.z())) {
            return false;
        }
        steps.add(new RouteStep(water ? RouteStep.Action.SWIM : RouteStep.Action.WALK, next));
        return true;
    }
}
