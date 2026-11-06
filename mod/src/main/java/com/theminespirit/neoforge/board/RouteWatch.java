package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.route.RouteChooser;
import com.theminespirit.core.board.route.RouteMarker;
import com.theminespirit.core.board.route.RoutePlan;
import com.theminespirit.core.board.route.RouteResource;
import com.theminespirit.core.board.route.RouteSpot;
import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;

final class RouteWatch {
    record Result(Set<String> events, Set<String> world) {
        Result {
            events = Set.copyOf(events);
            world = Set.copyOf(world);
        }
    }

    private RouteWatch() {}

    static Result tick(ServerLevel level, BlockCoord home) {
        RouteStore store = RouteStore.get(level);
        List<RouteSpot> distant = BiomeSpots.scan(level, home).stream()
                .filter(spot -> !BiomeSpots.sourceAt(level, home, spot.resource()))
                .toList();
        Map<RouteResource, RouteSpot> nearest = RouteChooser.nearestEach(home, distant);
        Set<String> events = new HashSet<>();
        Set<String> world = new HashSet<>();
        for (Map.Entry<RouteResource, RouteSpot> entry : nearest.entrySet()) {
            events.add(entry.getKey().foundEvent());
            world.add(entry.getKey().worldTag());
        }
        for (RouteResource resource : RouteResource.values()) {
            Optional<RouteMarker> marker = store.plan().marker(resource);
            Optional<RouteSpot> found = Optional.ofNullable(nearest.get(resource));
            if (marker.isEmpty()) {
                found.ifPresent(spot -> store.mark(resource, spot.point()));
                continue;
            }
            BlockCoord point = marker.orElseThrow().point();
            if (BiomeSpots.sourceAt(level, point, resource)) {
                continue;
            }
            events.add(resource.depletedEvent());
            if (found.isPresent() && !found.orElseThrow().point().equals(point)) {
                store.retarget(resource, found.orElseThrow().point());
                events.add(resource.retargetEvent());
            }
        }
        RoutePlan plan = store.plan();
        plan.markers().keySet().forEach(resource -> world.add(resource.worldTag()));
        return new Result(events, world);
    }
}
