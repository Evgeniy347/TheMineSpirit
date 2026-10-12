package com.theminespirit.core.perception;

import com.theminespirit.core.observation.BlockCoord;

public interface TerrainView {
    Terrain at(BlockCoord coord);
}
