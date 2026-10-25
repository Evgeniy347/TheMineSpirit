package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;

public interface CraterSurface {
    BlockCoord center();

    int radius();

    int groundLevel(int x, int z);

    boolean isAir(int x, int y, int z);

    boolean isProtected(int x, int y, int z);
}
