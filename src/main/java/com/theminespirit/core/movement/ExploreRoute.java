package com.theminespirit.core.movement;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.CatalogLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ExploreRoute {
    private ExploreRoute() {
    }

    public static List<BlockCoord> points(BlockCoord origin, int radius, int budget) {
        Objects.requireNonNull(origin, "origin не может быть null: маршрут разведки начинается от духа");
        if (radius < CatalogLimits.NAV_RADIUS_MIN || radius > CatalogLimits.NAV_RADIUS_MAX) {
            throw new IllegalArgumentException("Радиус маршрута " + radius + " вне диапазона ["
                    + CatalogLimits.NAV_RADIUS_MIN + ", " + CatalogLimits.NAV_RADIUS_MAX
                    + "]: больший обход кладёт серверный тик");
        }
        if (budget < CatalogLimits.EXPLORE_BUDGET_MIN || budget > CatalogLimits.EXPLORE_BUDGET_MAX) {
            throw new IllegalArgumentException("Бюджет маршрута " + budget + " вне диапазона ["
                    + CatalogLimits.EXPLORE_BUDGET_MIN + ", " + CatalogLimits.EXPLORE_BUDGET_MAX
                    + "]: бюджет считает точки обхода");
        }
        int spacing = spacing(radius, budget);
        int maxRing = (int) Math.ceil((double) radius / spacing);
        long radiusSq = (long) radius * radius;
        List<BlockCoord> points = new ArrayList<>(budget);
        for (int ring = 0; ring <= maxRing && points.size() < budget; ring++) {
            int r = ring * spacing;
            if (ring == 0) {
                points.add(origin);
                continue;
            }
            for (int dx = -r; dx <= r && points.size() < budget; dx += spacing) {
                add(points, origin, dx, -r, radiusSq);
            }
            for (int dz = -r + spacing; dz <= r && points.size() < budget; dz += spacing) {
                add(points, origin, r, dz, radiusSq);
            }
            for (int dx = r - spacing; dx >= -r && points.size() < budget; dx -= spacing) {
                add(points, origin, dx, r, radiusSq);
            }
            for (int dz = r - spacing; dz >= -r + spacing && points.size() < budget; dz -= spacing) {
                add(points, origin, -r, dz, radiusSq);
            }
        }
        return List.copyOf(points);
    }

    static int spacing(int radius, int budget) {
        int stepsAcross = (int) Math.ceil(Math.sqrt(budget));
        return Math.max(1, (int) Math.ceil(2.0 * radius / stepsAcross));
    }

    private static void add(List<BlockCoord> points, BlockCoord origin, int dx, int dz, long radiusSq) {
        if ((long) dx * dx + (long) dz * dz > radiusSq) {
            return;
        }
        points.add(new BlockCoord(origin.x() + dx, origin.y(), origin.z() + dz));
    }
}
