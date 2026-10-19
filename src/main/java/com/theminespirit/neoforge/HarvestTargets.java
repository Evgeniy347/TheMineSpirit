package com.theminespirit.neoforge;

import com.theminespirit.core.farming.Hydration;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Set;

interface HarvestTargets {
    int MAX_SLICES_PER_TICK = 4;

    List<BlockCoord> advance();

    boolean done();

    int slicesUsed();

    int maxSlicesPerTick();

    default String refusalReason() {
        return null;
    }

    default Hydration hydration() {
        return Hydration.of(Set.of());
    }
}
