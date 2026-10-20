package com.theminespirit.core.movement.network;

import com.theminespirit.core.movement.BodyStep;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Objects;

/**
 * Отрезок сети тропинок: клетки ног подряд (каждая следующая — шаг тела духа в обе стороны), длина в мере поиска пути
 * игры не больше {@link #MAX_LENGTH}. Концы отрезка — узлы графа сети.
 */
public record PathSegment(int id, List<BlockCoord> cells) {
    // mc: поиск пути игры обрывает маршрут на пройденной длине 32 (дальность следования духа), но длину узла
    // перезаписывает и худшим подходом к нему (PathFinder.findPath), и в доме с мебелью и лестницами путь срывается уже
    // на 15–20 шагах (замер 20.10, HouseMovementGameTests: спуск по парадной лестнице усадьбы). Отрезок — 12.
    public static final double MAX_LENGTH = 12.0;

    public PathSegment {
        Objects.requireNonNull(cells, "Клетки отрезка не могут быть null: отрезок — это путь по клеткам");
        cells = List.copyOf(cells);
        if (cells.size() < 2) {
            throw new IllegalArgumentException(
                    "отрезок " + id + " из " + cells.size() + " клеток: у отрезка два конца, нужно хотя бы две клетки");
        }
        if (lengthOf(cells, 0, cells.size() - 1) > MAX_LENGTH + 1e-9) {
            throw new IllegalArgumentException("отрезок " + id + " длиной " + lengthOf(cells, 0, cells.size() - 1)
                    + " длиннее " + MAX_LENGTH + ": поиск пути игры его не пройдёт, режь отрезок на части");
        }
    }

    public BlockCoord first() {
        return cells.get(0);
    }

    public BlockCoord last() {
        return cells.get(cells.size() - 1);
    }

    public double length() {
        return lengthOf(cells, 0, cells.size() - 1);
    }

    public double lengthBetween(int from, int to) {
        return lengthOf(cells, Math.min(from, to), Math.max(from, to));
    }

    public static double lengthOf(List<BlockCoord> cells, int from, int to) {
        double length = 0;
        for (int i = from; i < to; i++) {
            length += BodyStep.cost(cells.get(i), cells.get(i + 1));
        }
        return length;
    }
}
