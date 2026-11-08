package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class DistrictWorld implements FillOrder.World {
    private final Set<BlockCoord> solid = new HashSet<>();

    void floor(int x1, int z1, int x2, int z2, int y) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                solid.add(new BlockCoord(x, y, z));
            }
        }
    }

    void place(List<BlockCoord> cells) {
        solid.addAll(cells);
    }

    void remove(BlockCoord cell) {
        solid.remove(cell);
    }

    @Override
    public boolean solid(BlockCoord at) {
        return solid.contains(at);
    }

    @Override
    public boolean fluid(BlockCoord at) {
        return false;
    }
}
