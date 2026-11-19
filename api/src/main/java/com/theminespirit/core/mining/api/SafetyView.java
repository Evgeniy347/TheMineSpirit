package com.theminespirit.core.mining.api;

import com.theminespirit.core.observation.BlockCoord;

@FunctionalInterface
public interface SafetyView {
    Cell cellAt(BlockCoord coord);
}
