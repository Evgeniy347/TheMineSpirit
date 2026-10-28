package com.theminespirit.core.board.route;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;

public record RouteSpot(RouteResource resource, String biome, BlockCoord point) {
    public RouteSpot {
        Objects.requireNonNull(resource, "Ресурс места добычи не может быть null: без него не выбрать маршрут");
        Objects.requireNonNull(biome, "Биом места добычи не может быть null: он объясняет находку");
        Objects.requireNonNull(point, "Точка места добычи не может быть null: к ней ведёт маршрут");
    }

    public int distanceSquared(BlockCoord origin) {
        Objects.requireNonNull(origin, "Начало отсчёта не может быть null: расстояние считается от поселения");
        int dx = point.x() - origin.x();
        int dz = point.z() - origin.z();
        return dx * dx + dz * dz;
    }
}
