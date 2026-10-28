package com.theminespirit.core.mining;

import com.theminespirit.core.perception.Terrain;

public final class MineTerrain {
    private MineTerrain() {}

    public static Terrain of(Cell cell) {
        return switch (cell) {
            case SOLID, FALLING, HAZARD, PORTAL -> Terrain.SOLID;
            case WATER -> Terrain.WATER;
            case LAVA -> Terrain.LAVA;
            case PASSABLE, FIRE -> Terrain.AIR;
        };
    }
}
