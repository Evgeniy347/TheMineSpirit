package com.theminespirit.core.board.route;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;

public record QuarryCandidate(
        RouteResource resource, BlockCoord point, int radius, int reserve, int pathLength, String biome) {
    private static final int MILLI = 1000;

    public QuarryCandidate {
        Objects.requireNonNull(resource, "Ресурс кандидата не может быть null: поиск идёт за ресурсом");
        Objects.requireNonNull(point, "Точка кандидата не может быть null: место добычи ставится в точку");
        Objects.requireNonNull(biome, "Биом кандидата не может быть null: пусто — пустая строка");
        if (radius < 1 || radius > QuarryArea.MAX_RADIUS) {
            throw new IllegalArgumentException(
                    "Радиус кандидата " + radius + ": допустимо 1.." + QuarryArea.MAX_RADIUS);
        }
        if (reserve < 0 || pathLength < 0) {
            throw new IllegalArgumentException(
                    "Запас " + reserve + " и путь " + pathLength + ": отрицательных величин у места нет");
        }
    }

    public int valuePermille() {
        return (int) ((long) reserve * MILLI / Math.max(1, pathLength));
    }
}
