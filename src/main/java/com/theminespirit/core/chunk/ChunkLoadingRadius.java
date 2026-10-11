package com.theminespirit.core.chunk;

public final class ChunkLoadingRadius {
    public static final int AUTO = -1;
    public static final int MINIMUM = 1;

    private ChunkLoadingRadius() {
    }

    public static int effective(int configured, int simulationDistance) {
        int radius = configured > 0 ? configured : simulationDistance;
        return Math.max(radius, MINIMUM);
    }
}
