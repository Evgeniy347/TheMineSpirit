package com.theminespirit.core.board.route;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;

public record RouteMarker(RouteResource resource, BlockCoord point, boolean pathBuilt, boolean routeBuilt) {
    public RouteMarker {
        Objects.requireNonNull(resource, "Ресурс маршрута не может быть null: без него точка безымянна");
        Objects.requireNonNull(point, "Точка маршрута не может быть null: маркер ставится в мир");
        if (routeBuilt && !pathBuilt) {
            throw new IllegalArgumentException(
                    "Маршрут " + resource + " отмечен готовым без тропы: сначала тропа, потом точка добычи");
        }
    }

    public RouteMarker at(BlockCoord next) {
        Objects.requireNonNull(next, "Новая точка маршрута не может быть null: перенос ставит её в мир");
        return new RouteMarker(resource, next, false, false);
    }

    public RouteMarker withPathBuilt() {
        return new RouteMarker(resource, point, true, routeBuilt);
    }

    public RouteMarker withRouteBuilt() {
        return new RouteMarker(resource, point, true, true);
    }
}
