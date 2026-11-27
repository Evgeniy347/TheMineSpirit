package com.theminespirit.neoforge.crafting;

import com.theminespirit.core.crafting.CraftTuning;
import com.theminespirit.neoforge.BlockLocator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class StationFinder {
    private static final AtomicInteger CREATED = new AtomicInteger();

    private final BlockLocator.Search search;
    private final BlockPos center;
    private final List<BlockPos> located = new ArrayList<>();
    private List<BlockPos> stations = List.of();
    private int cursor;

    public StationFinder(Level level, BlockPos center, int radius, StationType type) {
        this.center = center;
        CREATED.incrementAndGet();
        this.search = BlockLocator.search(level, center, radius, Set.of(type.block()));
    }

    public static int created() {
        return CREATED.get();
    }

    public boolean step() {
        if (!search.done()) {
            located.addAll(search.step(CraftTuning.STATION_SLICES_PER_TICK));
        }
        if (search.done() && stations.isEmpty()) {
            stations = located.stream().sorted(order()).toList();
        }
        return search.done();
    }

    public Optional<BlockPos> found() {
        return stations.stream().findFirst();
    }

    public Optional<BlockPos> next() {
        if (cursor >= stations.size()) {
            return Optional.empty();
        }
        return Optional.of(stations.get(cursor++));
    }

    public int maxSlicesPerTick() {
        return search.maxSlicesPerTick();
    }

    private Comparator<BlockPos> order() {
        return Comparator.comparingDouble((BlockPos pos) -> pos.distSqr(center))
                .thenComparing(Comparator.naturalOrder());
    }
}
