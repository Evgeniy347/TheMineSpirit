package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;

@FunctionalInterface
public interface OreView {
    boolean isOre(BlockCoord coord);
}
