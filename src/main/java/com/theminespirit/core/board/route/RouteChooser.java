package com.theminespirit.core.board.route;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class RouteChooser {
    private RouteChooser() {}

    public static Optional<RouteSpot> nearest(RouteResource resource, BlockCoord origin, List<RouteSpot> spots) {
        Objects.requireNonNull(resource, "Ресурс не может быть null: ближайший ищется для него");
        Objects.requireNonNull(origin, "Начало отсчёта не может быть null: ближайший ищется от поселения");
        Objects.requireNonNull(spots, "Места добычи не могут быть null: нет находок — пустой список");
        return spots.stream()
                .filter(spot -> spot.resource() == resource)
                .min(Comparator.comparingInt((RouteSpot spot) -> spot.distanceSquared(origin))
                        .thenComparingInt(spot -> spot.point().x())
                        .thenComparingInt(spot -> spot.point().z())
                        .thenComparing(RouteSpot::biome));
    }

    public static Map<RouteResource, RouteSpot> nearestEach(BlockCoord origin, List<RouteSpot> spots) {
        Objects.requireNonNull(origin, "Начало отсчёта не может быть null: ближайшие ищутся от поселения");
        Objects.requireNonNull(spots, "Места добычи не могут быть null: нет находок — пустой список");
        Map<RouteResource, RouteSpot> nearest = new EnumMap<>(RouteResource.class);
        for (RouteResource resource : RouteResource.values()) {
            nearest(resource, origin, spots).ifPresent(spot -> nearest.put(resource, spot));
        }
        return nearest;
    }
}
