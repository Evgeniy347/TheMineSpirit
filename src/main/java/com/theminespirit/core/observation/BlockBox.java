package com.theminespirit.core.observation;

import java.util.Objects;
import java.util.Optional;

public record BlockBox(BlockCoord min, BlockCoord max) {
    public BlockBox {
        Objects.requireNonNull(min, "min не может быть null: куб задаётся двумя углами");
        Objects.requireNonNull(max, "max не может быть null: куб задаётся двумя углами");
        if (min.x() > max.x() || min.y() > max.y() || min.z() > max.z()) {
            throw new IllegalArgumentException("Угол min " + min + " лежит за углом max " + max
                    + ": куб с отрицательным размером не содержит блоков, проверьте порядок углов");
        }
    }

    public static BlockBox cube(BlockCoord center, int radius) {
        Objects.requireNonNull(center, "center не может быть null: куб строится вокруг точки");
        if (radius < 0) {
            throw new IllegalArgumentException(
                    "Радиус куба " + radius + " отрицателен: куб вокруг точки имеет радиус от 0");
        }
        return new BlockBox(
                new BlockCoord(center.x() - radius, center.y() - radius, center.z() - radius),
                new BlockCoord(center.x() + radius, center.y() + radius, center.z() + radius));
    }

    public Optional<BlockBox> clampY(int minY, int maxY) {
        int low = Math.max(min.y(), minY);
        int high = Math.min(max.y(), maxY);
        if (low > high) {
            return Optional.empty();
        }
        return Optional.of(new BlockBox(new BlockCoord(min.x(), low, min.z()), new BlockCoord(max.x(), high, max.z())));
    }

    public boolean contains(BlockCoord point) {
        return point.x() >= min.x()
                && point.x() <= max.x()
                && point.y() >= min.y()
                && point.y() <= max.y()
                && point.z() >= min.z()
                && point.z() <= max.z();
    }

    public boolean intersects(BlockBox other) {
        Objects.requireNonNull(other, "Второй куб не может быть null: пересечение проверяется у двух кубов");
        return min.x() <= other.max.x()
                && max.x() >= other.min.x()
                && min.y() <= other.max.y()
                && max.y() >= other.min.y()
                && min.z() <= other.max.z()
                && max.z() >= other.min.z();
    }

    public long volume() {
        return (long) (max.x() - min.x() + 1) * (max.y() - min.y() + 1) * (max.z() - min.z() + 1);
    }
}
