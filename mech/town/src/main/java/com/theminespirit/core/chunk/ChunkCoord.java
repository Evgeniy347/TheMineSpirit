package com.theminespirit.core.chunk;

public record ChunkCoord(int x, int z) implements Comparable<ChunkCoord> {
    @Override
    public int compareTo(ChunkCoord other) {
        int byX = Integer.compare(x, other.x);
        return byX != 0 ? byX : Integer.compare(z, other.z);
    }
}
