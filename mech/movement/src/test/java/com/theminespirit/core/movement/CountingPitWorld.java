package com.theminespirit.core.movement;

import java.util.HashSet;
import java.util.OptionalInt;
import java.util.Set;

final class CountingPitWorld implements PitWalkOut.World {
    private final PitWalkOut.World inner;
    private final Set<Long> cells = new HashSet<>();
    private int calls;

    CountingPitWorld(PitWalkOut.World inner) {
        this.inner = inner;
    }

    int surfaceCalls() {
        return calls;
    }

    int distinctSurfaceCells() {
        return cells.size();
    }

    @Override
    public OptionalInt groundY(int x, int z, int from) {
        return inner.groundY(x, z, from);
    }

    @Override
    public int surfaceY(int x, int z) {
        calls++;
        cells.add((((long) x) << 32) ^ (z & 0xffffffffL));
        return inner.surfaceY(x, z);
    }

    @Override
    public boolean clear(int x, int y, int z) {
        return inner.clear(x, y, z);
    }
}
