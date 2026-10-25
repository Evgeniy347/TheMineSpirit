package com.theminespirit.core.movement;

import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class BodyStep {
    public static final int MAX_DROP = 3;
    private static final double DIAGONAL = Math.sqrt(2.0);

    private BodyStep() {}

    public static @Nullable BlockCoord step(BlockCoord from, int dx, int dz, SafetyView view) {
        Objects.requireNonNull(from, "Исходная клетка шага не может быть null: неизвестно, откуда шагать");
        Objects.requireNonNull(view, "Мир не может быть null: по нему проверяется, куда можно встать");
        int x = from.x() + dx;
        int z = from.z() + dz;
        BlockCoord level = new BlockCoord(x, from.y(), z);
        if (MiningSafety.canStand(level, view)) {
            return level;
        }
        BlockCoord up = new BlockCoord(x, from.y() + 1, z);
        if (view.cellAt(new BlockCoord(from.x(), from.y() + 2, from.z())) == Cell.PASSABLE
                && MiningSafety.canStand(up, view)) {
            return up;
        }
        for (int drop = 1; drop <= MAX_DROP; drop++) {
            BlockCoord through = new BlockCoord(x, from.y() - drop + 1, z);
            if (view.cellAt(new BlockCoord(x, from.y() + 1, z)) != Cell.PASSABLE
                    || view.cellAt(through) != Cell.PASSABLE) {
                return null;
            }
            BlockCoord down = new BlockCoord(x, from.y() - drop, z);
            if (MiningSafety.canStand(down, view)) {
                return down;
            }
        }
        return null;
    }

    public static @Nullable BlockCoord twoWay(BlockCoord from, int dx, int dz, SafetyView view) {
        BlockCoord next = step(from, dx, dz, view);
        if (next == null || Math.abs(next.y() - from.y()) > 1) {
            return null;
        }
        return from.equals(step(next, -dx, -dz, view)) ? next : null;
    }

    public static double cost(BlockCoord from, BlockCoord to) {
        return from.y() == to.y() ? 1.0 : DIAGONAL;
    }
}
