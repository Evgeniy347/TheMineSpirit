package com.theminespirit.core.forestry;

import com.theminespirit.core.observation.BlockCoord;

public interface TreeView {
    boolean isLog(BlockCoord coord);

    default boolean isAnyLog(BlockCoord coord) {
        return isLog(coord);
    }

    boolean isNaturalLeaves(BlockCoord coord);

    default boolean isFoliageObstacle(BlockCoord coord) {
        return isNaturalLeaves(coord);
    }

    boolean isSoil(BlockCoord coord);

    boolean isArtificial(BlockCoord coord);

    boolean isEmpty(BlockCoord coord);
}
