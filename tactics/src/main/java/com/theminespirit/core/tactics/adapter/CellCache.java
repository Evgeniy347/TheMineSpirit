package com.theminespirit.core.tactics.adapter;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.perception.Terrain;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class CellCache {
    public static final int TTL_TICKS = 100;
    public static final int MAX_ENTRIES = 4096;

    private record Entry(Terrain terrain, long readAt) {}

    private final Map<BlockCoord, Entry> entries = new HashMap<>();

    public @Nullable Terrain fresh(BlockCoord coord, long now) {
        Entry entry = entries.get(coord);
        return entry != null && now - entry.readAt() < TTL_TICKS ? entry.terrain() : null;
    }

    public void put(BlockCoord coord, Terrain terrain, long now) {
        if (entries.size() >= MAX_ENTRIES) {
            entries.clear();
        }
        entries.put(coord, new Entry(terrain, now));
    }

    public void invalidate(BlockCoord coord) {
        entries.remove(coord);
    }

    public int size() {
        return entries.size();
    }
}
