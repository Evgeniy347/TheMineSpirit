package com.theminespirit.core.observation;

public record BlockCoord(int x, int y, int z) implements Comparable<BlockCoord> {
    @Override
    public int compareTo(BlockCoord other) {
        int byX = Integer.compare(x, other.x);
        if (byX != 0) {
            return byX;
        }
        int byY = Integer.compare(y, other.y);
        if (byY != 0) {
            return byY;
        }
        return Integer.compare(z, other.z);
    }
}
