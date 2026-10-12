package com.theminespirit.core.farming;

import com.theminespirit.core.observation.BlockCoord;

public interface FarmView {
    FarmTile tileAt(BlockCoord ground);
}
