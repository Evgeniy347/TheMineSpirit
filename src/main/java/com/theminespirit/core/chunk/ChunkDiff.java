package com.theminespirit.core.chunk;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public record ChunkDiff(Set<ChunkCoord> toAdd, Set<ChunkCoord> toRemove) {
    public ChunkDiff {
        toAdd = Collections.unmodifiableSet(new LinkedHashSet<>(toAdd));
        toRemove = Collections.unmodifiableSet(new LinkedHashSet<>(toRemove));
    }

    public static ChunkDiff between(Set<ChunkCoord> current, Set<ChunkCoord> desired) {
        Set<ChunkCoord> toAdd = new LinkedHashSet<>(desired);
        toAdd.removeAll(current);
        Set<ChunkCoord> toRemove = new LinkedHashSet<>(current);
        toRemove.removeAll(desired);
        return new ChunkDiff(toAdd, toRemove);
    }

    public static ChunkDiff initial(Set<ChunkCoord> desired) {
        return new ChunkDiff(desired, Set.of());
    }

    public boolean isEmpty() {
        return toAdd.isEmpty() && toRemove.isEmpty();
    }
}
