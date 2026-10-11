package com.theminespirit.core.forestry;

import com.theminespirit.core.observation.BlockCoord;

public interface TreeView {
    boolean isLog(BlockCoord coord);

    boolean isNaturalLeaves(BlockCoord coord);

    boolean isSoil(BlockCoord coord);

    boolean isArtificial(BlockCoord coord);
}
