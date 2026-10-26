package com.theminespirit.core.route;

import java.util.HashMap;
import java.util.Map;

final class GridWorld implements RouteWorld {
    static final int DIRT = RouteCell.solid(15);
    private final int ground;
    private final Map<Long, Integer> changed = new HashMap<>();

    private GridWorld(int ground) {
        this.ground = ground;
    }

    static GridWorld flat(int ground) {
        return new GridWorld(ground);
    }

    GridWorld fill(int x0, int y0, int z0, int x1, int y1, int z1, int cell) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                    set(x, y, z, cell);
                }
            }
        }
        return this;
    }

    void set(int x, int y, int z, int cell) {
        changed.put(LongIntTable.key(x, y, z), cell);
    }

    @Override
    public int cell(int x, int y, int z) {
        Integer cell = changed.get(LongIntTable.key(x, y, z));
        if (cell != null) {
            return cell;
        }
        return y < ground ? DIRT : RouteCell.AIR;
    }
}
