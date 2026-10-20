package com.theminespirit.neoforge;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.phys.Vec3;

// Маршрут по сети тропинок, пройденный по точкам: каждая следующая — не дальше отрезка сети (≤ пути игры 32).
final class NetworkLegs {
    private static final double POINT_ARRIVAL = 1.2;

    private final List<BlockCoord> points;
    private int index;

    NetworkLegs(List<BlockCoord> points) {
        Objects.requireNonNull(points, "Точки маршрута сети не могут быть null: по ним идёт дух");
        if (points.isEmpty()) {
            throw new IllegalArgumentException("маршрут сети без точек: идти некуда, такой маршрут не заводят");
        }
        this.points = List.copyOf(points);
    }

    // Текущая точка — только пока не done(): пройдены все — дальше путь к самой цели.
    Vec3 current() {
        return center(points.get(index));
    }

    boolean done() {
        return index >= points.size();
    }

    void advance(Vec3 position) {
        while (!done() && position.distanceTo(center(points.get(index))) <= POINT_ARRIVAL) {
            index++;
        }
    }

    BlockCoord from() {
        return points.get(Math.max(0, index - 1));
    }

    BlockCoord to() {
        return points.get(Math.min(index, points.size() - 1));
    }

    double length() {
        double length = 0;
        for (int i = 1; i < points.size(); i++) {
            length += center(points.get(i - 1)).distanceTo(center(points.get(i)));
        }
        return length;
    }

    private static Vec3 center(BlockCoord cell) {
        return new Vec3(cell.x() + 0.5, cell.y(), cell.z() + 0.5);
    }
}
