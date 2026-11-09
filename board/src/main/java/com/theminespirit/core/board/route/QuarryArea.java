package com.theminespirit.core.board.route;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import java.util.Objects;

public record QuarryArea(RouteResource resource, BlockCoord point, int radius) {
    public static final int DEFAULT_RADIUS = 4;
    public static final int MAX_RADIUS = 16;

    public QuarryArea {
        Objects.requireNonNull(resource, "Ресурс области не может быть null: у области добычи один ресурс");
        Objects.requireNonNull(point, "Точка области не может быть null: от неё считается квадрат добычи");
        if (radius < 1 || radius > MAX_RADIUS) {
            throw new IllegalArgumentException("Радиус области добычи " + radius + ": допустимо 1.." + MAX_RADIUS
                    + ", иначе карьер не влезет в оценку и в путь духа");
        }
    }

    public static QuarryArea at(RouteResource resource, BlockCoord point) {
        return new QuarryArea(resource, point, DEFAULT_RADIUS);
    }

    public ColumnMask mask() {
        return ColumnMask.rect(point.x() - radius, point.z() - radius, point.x() + radius, point.z() + radius);
    }

    public int distance(BlockCoord origin) {
        Objects.requireNonNull(origin, "Начало отсчёта не может быть null: расстояние считается от поселения");
        return Math.abs(point.x() - origin.x()) + Math.abs(point.z() - origin.z());
    }
}
