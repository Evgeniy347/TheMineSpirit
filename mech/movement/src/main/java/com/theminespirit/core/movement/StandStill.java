package com.theminespirit.core.movement;

import com.theminespirit.core.observation.BlockCoord;
import org.jspecify.annotations.Nullable;

public final class StandStill {
    public static final int LIMIT_TICKS = 20;
    private @Nullable BlockCoord last;
    private int ticks;

    public int limit() {
        return LIMIT_TICKS;
    }

    public boolean stuck(BlockCoord feet) {
        if (feet.equals(last)) {
            ticks++;
        } else {
            last = feet;
            ticks = 1;
        }
        return ticks >= LIMIT_TICKS;
    }

    public void reset() {
        last = null;
        ticks = 0;
    }
}
