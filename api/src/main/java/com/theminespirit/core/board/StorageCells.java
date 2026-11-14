package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Set;

public final class StorageCells {
    private StorageCells() {}

    public static Set<BlockCoord> storage(Set<BlockCoord> warehouse, Set<BlockCoord> yard) {
        return warehouse.isEmpty() ? yard : warehouse;
    }
}
