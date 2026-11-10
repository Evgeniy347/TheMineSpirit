package com.theminespirit.core.board.route;

import com.theminespirit.core.board.graph.GraphNames;
import com.theminespirit.core.observation.BlockCoord;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class QuarrySites {
    public record Feed(
            Set<String> events, Set<String> world, Map<String, BlockCoord> nearest, Map<String, Integer> distances) {
        public Feed {
            events = Set.copyOf(events);
            world = Set.copyOf(world);
            nearest = Map.copyOf(nearest);
            distances = Map.copyOf(distances);
        }

        public static Feed empty() {
            return new Feed(Set.of(), Set.of(), Map.of(), Map.of());
        }
    }

    private QuarrySites() {}

    public static Feed feed(List<RouteMarker> markers, BlockCoord home) {
        Objects.requireNonNull(markers, "Маркеры не могут быть null: пусто — пустой список");
        Objects.requireNonNull(home, "Дом поселения не может быть null: от него считается путь");
        Set<String> events = new HashSet<>();
        Set<String> world = new HashSet<>();
        Map<String, BlockCoord> nearest = new HashMap<>();
        Map<String, Integer> distances = new HashMap<>();
        for (RouteMarker marker : markers) {
            events.add(marker.resource().foundEvent());
            world.add(marker.resource().worldTag());
            String tag = sourceTag(marker.resource()).orElseThrow();
            nearest.put(tag, marker.point());
            distances.put(tag, QuarryPaths.length(home, marker.point()));
        }
        return new Feed(events, world, nearest, distances);
    }

    public static Optional<String> sourceTag(RouteResource resource) {
        Objects.requireNonNull(resource, "Ресурс не может быть null: у него спрашивают тег места добычи");
        return switch (resource) {
            case DIRT -> Optional.of(GraphNames.SOIL);
            case SAND -> Optional.of("sand");
            case SNOW -> Optional.of("snow");
        };
    }
}
