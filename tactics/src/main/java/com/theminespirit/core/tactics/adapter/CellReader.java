package com.theminespirit.core.tactics.adapter;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.perception.Terrain;
import com.theminespirit.core.perception.TerrainView;
import java.util.Optional;

public final class CellReader {
    private final TerrainView world;
    private final CellCache cache;
    private final ReadBudget budget;
    private final long now;
    private boolean cutByBudget;

    public CellReader(TerrainView world, CellCache cache, ReadBudget budget, long now) {
        this.world = world;
        this.cache = cache;
        this.budget = budget;
        this.now = now;
    }

    public Optional<Terrain> at(BlockCoord coord) {
        Terrain cached = cache.fresh(coord, now);
        if (cached != null) {
            return Optional.of(cached);
        }
        if (!budget.tryTake()) {
            cutByBudget = true;
            return Optional.empty();
        }
        Terrain read = world.at(coord);
        if (read == Terrain.UNLOADED) {
            return Optional.empty();
        }
        cache.put(coord, read, now);
        return Optional.of(read);
    }

    public boolean cutByBudget() {
        return cutByBudget;
    }

    public ReadBudget budget() {
        return budget;
    }
}
