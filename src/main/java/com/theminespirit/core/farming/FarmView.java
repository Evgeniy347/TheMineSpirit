package com.theminespirit.core.farming;

import com.theminespirit.core.observation.BlockCoord;

public interface FarmView {
    FarmTile tileAt(BlockCoord ground);

    default int columnHeight(BlockCoord ground) {
        return 1;
    }
}
