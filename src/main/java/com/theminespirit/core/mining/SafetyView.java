package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;

public interface SafetyView {
    Cell cellAt(BlockCoord coord);
}
