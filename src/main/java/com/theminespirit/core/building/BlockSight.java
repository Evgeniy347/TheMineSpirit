package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.perception.Terrain;
import com.theminespirit.core.perception.TerrainView;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.adapter.Visibility;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public final class BlockSight {
    public static final double EYE_HEIGHT = 1.62;
    private static final double INSIDE = 1.0E-9;
    private static final int MAX_STEPS = 64;

    private BlockSight() {}

    public static Point eyes(BlockCoord feet) {
        Objects.requireNonNull(feet, "Ноги духа не могут быть null: без них неизвестна точка глаз");
        return new Point(feet.x() + 0.5, feet.y() + EYE_HEIGHT, feet.z() + 0.5);
    }

    public static Visibility visible(Point eye, BlockCoord target, TerrainView world, Predicate<BlockCoord> passable) {
        Objects.requireNonNull(eye, "Глаза духа не могут быть null: без них неизвестно, откуда смотреть");
        Objects.requireNonNull(target, "Цель не может быть null: без неё неизвестно, что видно");
        Objects.requireNonNull(world, "Мир не может быть null: по нему проверяется проход луча");
        Objects.requireNonNull(passable, "Проходимые клетки не могут быть null: пустое правило задаётся ложью");
        Visibility result = Visibility.BLOCKED;
        for (Point face : faces(target)) {
            Visibility seen = walk(eye, face, target, world, passable);
            if (seen == Visibility.CLEAR) {
                return Visibility.CLEAR;
            }
            if (seen == Visibility.UNKNOWN) {
                result = Visibility.UNKNOWN;
            }
        }
        return result;
    }

    private static List<Point> faces(BlockCoord target) {
        double cx = target.x() + 0.5;
        double cy = target.y() + 0.5;
        double cz = target.z() + 0.5;
        double lowX = target.x() + INSIDE;
        double highX = target.x() + 1 - INSIDE;
        double lowY = target.y() + INSIDE;
        double highY = target.y() + 1 - INSIDE;
        double lowZ = target.z() + INSIDE;
        double highZ = target.z() + 1 - INSIDE;
        return List.of(
                new Point(lowX, cy, cz),
                new Point(highX, cy, cz),
                new Point(cx, lowY, cz),
                new Point(cx, highY, cz),
                new Point(cx, cy, lowZ),
                new Point(cx, cy, highZ));
    }

    private static Visibility walk(
            Point from, Point to, BlockCoord target, TerrainView world, Predicate<BlockCoord> passable) {
        int x = (int) Math.floor(from.x());
        int y = (int) Math.floor(from.y());
        int z = (int) Math.floor(from.z());
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double dz = to.z() - from.z();
        int stepX = dx > 0 ? 1 : dx < 0 ? -1 : 0;
        int stepY = dy > 0 ? 1 : dy < 0 ? -1 : 0;
        int stepZ = dz > 0 ? 1 : dz < 0 ? -1 : 0;
        double tMaxX = firstBoundary(from.x(), x, dx, stepX);
        double tMaxY = firstBoundary(from.y(), y, dy, stepY);
        double tMaxZ = firstBoundary(from.z(), z, dz, stepZ);
        double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(dx);
        double tDeltaY = stepY == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(dy);
        double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(dz);
        for (int step = 0; step < MAX_STEPS; step++) {
            if (x == target.x() && y == target.y() && z == target.z()) {
                return Visibility.CLEAR;
            }
            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                x += stepX;
                tMaxX += tDeltaX;
            } else if (tMaxY <= tMaxZ) {
                y += stepY;
                tMaxY += tDeltaY;
            } else {
                z += stepZ;
                tMaxZ += tDeltaZ;
            }
            if (x == target.x() && y == target.y() && z == target.z()) {
                return Visibility.CLEAR;
            }
            BlockCoord cell = new BlockCoord(x, y, z);
            if (passable.test(cell)) {
                continue;
            }
            Terrain terrain = world.at(cell);
            if (terrain == Terrain.UNLOADED) {
                return Visibility.UNKNOWN;
            }
            if (terrain == Terrain.SOLID) {
                return Visibility.BLOCKED;
            }
        }
        return Visibility.UNKNOWN;
    }

    private static double firstBoundary(double from, int cell, double delta, int step) {
        if (step == 0) {
            return Double.POSITIVE_INFINITY;
        }
        double distance = step > 0 ? cell + 1 - from : from - cell;
        return distance / Math.abs(delta);
    }
}
