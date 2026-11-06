package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;

public interface CraterSurface {
    BlockCoord center();

    int radius();

    int groundLevel(int x, int z);

    boolean isAir(int x, int y, int z);

    default boolean isFluid(int x, int y, int z) {
        return false;
    }

    default boolean isFillable(int x, int y, int z) {
        return isAir(x, y, z) || isFluid(x, y, z);
    }

    boolean isProtected(int x, int y, int z);
}
