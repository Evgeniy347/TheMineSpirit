package com.theminespirit.core.board.route;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;

public record QuarrySite(RouteResource resource, BlockCoord point, int radius, String biome, int initialReserve) {
    public QuarrySite {
        Objects.requireNonNull(resource, "Ресурс места добычи не может быть null: у места один ресурс");
        Objects.requireNonNull(point, "Точка места добычи не может быть null: к ней ведёт тропа");
        Objects.requireNonNull(biome, "Биом места добычи не может быть null: пусто — пустая строка");
        if (radius < 1 || radius > QuarryArea.MAX_RADIUS) {
            throw new IllegalArgumentException("Радиус места добычи " + radius + ": допустимо 1.."
                    + QuarryArea.MAX_RADIUS + ", иначе оценка запаса расходится с копанием");
        }
        if (initialReserve < 0) {
            throw new IllegalArgumentException("Начальный запас " + initialReserve
                    + ": остаток считается от неотрицательного запаса, иначе проценты врут");
        }
    }

    public QuarryArea area() {
        return new QuarryArea(resource, point, radius);
    }
}
