// lib: Из Baritone (github.com/cabaletta/baritone, pathing/path/CutoffPath.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Objects;

public final class NavPathCutoff {
    private NavPathCutoff() {}

    public static NavPath atAvailable(NavPath path, NavWorld world) {
        Objects.requireNonNull(path, "Путь не может быть null: резать нечего");
        Objects.requireNonNull(world, "Мир не может быть null: по нему ищется граница");
        for (int i = 0; i < path.positions().size(); i++) {
            BlockCoord cell = path.positions().get(i);
            if (!world.available(cell.x(), cell.z())) {
                return new NavPath(path.positions().subList(0, Math.max(1, i)), path.cost(), false);
            }
        }
        return path;
    }

    public static NavPath staticCutoff(NavPath path, NavGoal goal, int minLength, double factor) {
        Objects.requireNonNull(path, "Путь не может быть null: резать нечего");
        Objects.requireNonNull(goal, "Цель не может быть null: без неё не понять, дошли ли");
        if (minLength < 2) {
            throw new IllegalArgumentException("Минимальная длина отрезка " + minLength + " меньше двух клеток");
        }
        if (!(factor > 0) || factor > 1) {
            throw new IllegalArgumentException("Доля отрезка " + factor + " вне (0, 1]");
        }
        if (path.length() < minLength
                || goal.isInGoal(path.end().x(), path.end().y(), path.end().z())) {
            return path;
        }
        int length = (int) ((path.length() - minLength) * factor) + minLength;
        length = Math.max(minLength, Math.min(path.length(), length));
        List<BlockCoord> positions = path.positions().subList(0, length);
        return new NavPath(positions, path.cost(), false);
    }
}
