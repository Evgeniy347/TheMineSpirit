package com.theminespirit.core.board.route;

import com.theminespirit.core.observation.BlockCoord;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

public final class RoutePlan {
    private final Map<RouteResource, RouteMarker> markers;

    private RoutePlan(Map<RouteResource, RouteMarker> markers) {
        this.markers = Map.copyOf(markers);
    }

    public static RoutePlan empty() {
        return new RoutePlan(Map.of());
    }

    public static RoutePlan of(List<RouteMarker> markers) {
        Objects.requireNonNull(markers, "Маркеры не могут быть null: пустой план задают пустым списком");
        Map<RouteResource, RouteMarker> byResource = new EnumMap<>(RouteResource.class);
        for (RouteMarker marker : markers) {
            RouteMarker before = byResource.put(marker.resource(), marker);
            if (before != null) {
                throw new IllegalArgumentException(
                        "Маркер " + marker.resource() + " объявлен дважды: у ресурса одна точка добычи");
            }
        }
        return new RoutePlan(byResource);
    }

    public Optional<RouteMarker> marker(RouteResource resource) {
        Objects.requireNonNull(resource, "Ресурс не может быть null: у него спрашивают маркер");
        return Optional.ofNullable(markers.get(resource));
    }

    public Optional<BlockCoord> point(RouteResource resource) {
        return marker(resource).map(RouteMarker::point);
    }

    public Map<RouteResource, RouteMarker> markers() {
        return markers;
    }

    public boolean pathDone(RouteResource resource) {
        return marker(resource).map(RouteMarker::pathBuilt).orElse(false);
    }

    public boolean routeDone(RouteResource resource) {
        return marker(resource).map(RouteMarker::routeBuilt).orElse(false);
    }

    public RoutePlan mark(RouteResource resource, BlockCoord point) {
        Objects.requireNonNull(resource, "Ресурс не может быть null: маркер ставится для него");
        Objects.requireNonNull(point, "Точка не может быть null: маркер ставится в мир");
        return mark(new QuarrySite(resource, point, QuarryArea.DEFAULT_RADIUS, "", 0));
    }

    public RoutePlan mark(QuarrySite site) {
        Objects.requireNonNull(site, "Место добычи не может быть null: маркер ставится для него");
        Map<RouteResource, RouteMarker> next = new EnumMap<>(RouteResource.class);
        next.putAll(markers);
        RouteMarker before = next.get(site.resource());
        if (before != null && before.point().equals(site.point())) {
            return this;
        }
        next.put(
                site.resource(),
                new RouteMarker(
                        site.resource(),
                        site.point(),
                        false,
                        false,
                        site.radius(),
                        site.biome(),
                        site.initialReserve(),
                        0L));
        return new RoutePlan(next);
    }

    public RoutePlan unmark(RouteResource resource) {
        Objects.requireNonNull(resource, "Ресурс не может быть null: место снимается у него");
        if (!markers.containsKey(resource)) {
            return this;
        }
        Map<RouteResource, RouteMarker> next = new EnumMap<>(RouteResource.class);
        next.putAll(markers);
        next.remove(resource);
        return new RoutePlan(next);
    }

    public RoutePlan alerted(RouteResource resource, long tick) {
        return update(resource, marker -> marker.withAlertedAt(tick));
    }

    public RoutePlan pathBuilt(RouteResource resource) {
        return update(resource, RouteMarker::withPathBuilt);
    }

    public RoutePlan routeBuilt(RouteResource resource) {
        return update(resource, RouteMarker::withRouteBuilt);
    }

    private RoutePlan update(RouteResource resource, UnaryOperator<RouteMarker> change) {
        Objects.requireNonNull(resource, "Ресурс не может быть null: отметка ставится для него");
        RouteMarker before = Objects.requireNonNull(
                markers.get(resource),
                "Маркер " + resource + " не поставлен: сначала точка на плане, потом тропа и добыча");
        Map<RouteResource, RouteMarker> next = new EnumMap<>(RouteResource.class);
        next.putAll(markers);
        next.put(resource, change.apply(before));
        return new RoutePlan(next);
    }
}
