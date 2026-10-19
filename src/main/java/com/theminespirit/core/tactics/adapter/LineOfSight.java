package com.theminespirit.core.tactics.adapter;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.perception.Terrain;
import com.theminespirit.core.tactics.Point;
import java.util.Optional;

public final class LineOfSight {
    public static final double STEP = 1.0;

    private LineOfSight() {}

    public static Visibility trace(Point from, Point to, CellReader reader) {
        double distance = from.distanceTo(to);
        int steps = (int) Math.ceil(distance / STEP);
        BlockCoord last = null;
        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            BlockCoord cell = new BlockCoord(
                    (int) Math.floor(from.x() + (to.x() - from.x()) * t),
                    (int) Math.floor(from.y() + (to.y() - from.y()) * t),
                    (int) Math.floor(from.z() + (to.z() - from.z()) * t));
            if (cell.equals(last)) {
                continue;
            }
            last = cell;
            Optional<Terrain> terrain = reader.at(cell);
            if (terrain.isEmpty()) {
                return Visibility.UNKNOWN;
            }
            if (terrain.get() == Terrain.SOLID) {
                return Visibility.BLOCKED;
            }
        }
        return Visibility.CLEAR;
    }
}
