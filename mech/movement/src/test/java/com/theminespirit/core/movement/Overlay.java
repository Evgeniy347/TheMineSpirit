package com.theminespirit.core.movement;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.route.RouteWorld;
import java.util.HashMap;
import java.util.Map;

final class Overlay implements RouteWorld {
    private final FuzzWorld base;
    private final Map<BlockCoord, FuzzWorld.Cell> changes = new HashMap<>();

    Overlay(FuzzWorld base) {
        this.base = base;
    }

    void set(BlockCoord cell, FuzzWorld.Cell value) {
        changes.put(cell, value);
    }

    @Override
    public int cell(int x, int y, int z) {
        FuzzWorld.Cell change = changes.get(new BlockCoord(x, y, z));
        return (change == null ? base.cellAt(x, y, z) : change).route();
    }
}
