package com.theminespirit.core.planner;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.WorldObservation;
import java.util.List;

public final class Observations {
    private Observations() {}

    public static WorldObservation empty(String botId) {
        return new WorldObservation(
                botId, 0L, new BlockCoord(0, 64, 0), List.of(), List.of(), List.of(), List.of(), 32);
    }
}
