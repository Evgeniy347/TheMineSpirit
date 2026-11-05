package com.theminespirit.core.perception;

import com.theminespirit.core.observation.BlockCoord;

@FunctionalInterface
public interface TerrainView {
    Terrain at(BlockCoord coord);
}
