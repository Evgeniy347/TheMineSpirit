package com.theminespirit.core.board.route;

import com.theminespirit.core.observation.BlockCoord;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class RoutePath {
    public static final int WIDTH = 3;

    private RoutePath() {}

    public static List<BlockCoord> tiles(BlockCoord from, BlockCoord to) {
        Objects.requireNonNull(from, "Начало тропы не может быть null: от него кладут дорогу");
        Objects.requireNonNull(to, "Конец тропы не может быть null: к нему ведёт дорога");
        Set<BlockCoord> cells = new LinkedHashSet<>();
        int stepX = Integer.signum(to.x() - from.x());
        int stepZ = Integer.signum(to.z() - from.z());
        int x = from.x();
        int z = from.z();
        while (x != to.x()) {
            add(cells, x, from.y(), z);
            x += stepX;
        }
        while (z != to.z()) {
            add(cells, x, from.y(), z);
            z += stepZ;
        }
        add(cells, to.x(), from.y(), to.z());
        return List.copyOf(cells);
    }

    private static void add(Set<BlockCoord> cells, int x, int y, int z) {
        for (int offset = -WIDTH / 2; offset <= WIDTH / 2; offset++) {
            cells.add(new BlockCoord(x + offset, y, z));
            cells.add(new BlockCoord(x, y, z + offset));
        }
    }
}
