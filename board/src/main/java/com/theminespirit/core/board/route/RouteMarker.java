package com.theminespirit.core.board.route;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;

public record RouteMarker(
        RouteResource resource,
        BlockCoord point,
        boolean pathBuilt,
        boolean routeBuilt,
        int radius,
        String biome,
        int initialReserve,
        long alertedAt) {
    public RouteMarker {
        Objects.requireNonNull(resource, "Ресурс маршрута не может быть null: без него точка безымянна");
        Objects.requireNonNull(point, "Точка маршрута не может быть null: маркер ставится в мир");
        Objects.requireNonNull(biome, "Биом маршрута не может быть null: пусто — пустая строка");
        if (routeBuilt && !pathBuilt) {
            throw new IllegalArgumentException(
                    "Маршрут " + resource + " отмечен готовым без тропы: сначала тропа, потом точка добычи");
        }
        if (radius < 1 || radius > QuarryArea.MAX_RADIUS) {
            throw new IllegalArgumentException("Радиус маркера " + radius + ": допустимо 1.." + QuarryArea.MAX_RADIUS);
        }
        if (initialReserve < 0) {
            throw new IllegalArgumentException(
                    "Начальный запас маркера " + initialReserve + ": остаток считается от неотрицательного запаса");
        }
    }

    public RouteMarker(RouteResource resource, BlockCoord point, boolean pathBuilt, boolean routeBuilt) {
        this(resource, point, pathBuilt, routeBuilt, QuarryArea.DEFAULT_RADIUS, "", 0, 0L);
    }

    public RouteMarker at(BlockCoord next) {
        Objects.requireNonNull(next, "Новая точка маршрута не может быть null: перенос ставит её в мир");
        return new RouteMarker(resource, next, false, false, radius, biome, initialReserve, 0L);
    }

    public RouteMarker withPathBuilt() {
        return new RouteMarker(resource, point, true, routeBuilt, radius, biome, initialReserve, alertedAt);
    }

    public RouteMarker withRouteBuilt() {
        return new RouteMarker(resource, point, true, true, radius, biome, initialReserve, alertedAt);
    }

    public RouteMarker withAlertedAt(long tick) {
        return new RouteMarker(resource, point, pathBuilt, routeBuilt, radius, biome, initialReserve, tick);
    }

    public QuarrySite site() {
        return new QuarrySite(resource, point, radius, biome, initialReserve);
    }
}
