package com.theminespirit.core.movement.network;

import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.Set;

// Мир тестов сети: всё проходимо, кроме заданных твёрдых блоков; считает чтения клеток для тестов на масштаб.
final class GridWorld implements SafetyView {
    private final Set<BlockCoord> solid = new HashSet<>();
    private long reads;

    GridWorld solid(int x, int y, int z) {
        solid.add(new BlockCoord(x, y, z));
        return this;
    }

    GridWorld clear(int x, int y, int z) {
        solid.remove(new BlockCoord(x, y, z));
        return this;
    }

    GridWorld slab(int y, int x1, int z1, int x2, int z2) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                solid(x, y, z);
            }
        }
        return this;
    }

    long reads() {
        return reads;
    }

    @Override
    public Cell cellAt(BlockCoord coord) {
        reads++;
        return solid.contains(coord) ? Cell.SOLID : Cell.PASSABLE;
    }

    // Двухэтажный дом 20×5: пол первого этажа y=0 (ноги y=1), пол второго y=4 (ноги y=5), лестница вдоль z=0
    // от x=10 вверх до x=13; над лестницей во втором полу проём x=9..12, z=0. Дверь — (0, 1, 2).
    static GridWorld twoFloors() {
        GridWorld world = new GridWorld().slab(0, 0, 0, 19, 4).slab(4, 0, 0, 19, 4);
        for (int x = 9; x <= 12; x++) {
            world.clear(x, 4, 0);
        }
        return world.solid(10, 1, 0).solid(11, 2, 0).solid(12, 3, 0);
    }

    static BlockCoord cell(int x, int y, int z) {
        return new BlockCoord(x, y, z);
    }
}
