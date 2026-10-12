package com.theminespirit.neoforge.crafting;

import com.theminespirit.core.crafting.CraftTuning;
import com.theminespirit.neoforge.BlockLocator;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class StationFinder {
    private final BlockLocator.Search search;
    private final BlockPos center;
    private BlockPos nearest;

    public StationFinder(Level level, BlockPos center, int radius, StationType type) {
        this.center = center;
        this.search = BlockLocator.search(level, center, radius, Set.of(type.block()));
    }

    public boolean step() {
        if (!search.done()) {
            for (BlockPos found : search.step(CraftTuning.STATION_SLICES_PER_TICK)) {
                nearest = nearest == null ? found : closer(nearest, found);
            }
        }
        return search.done();
    }

    public Optional<BlockPos> found() {
        return Optional.ofNullable(nearest);
    }

    public int maxSlicesPerTick() {
        return search.maxSlicesPerTick();
    }

    private BlockPos closer(BlockPos first, BlockPos second) {
        return Comparator.comparingDouble((BlockPos pos) -> pos.distSqr(center)).thenComparing(Comparator.naturalOrder())
                .compare(first, second) <= 0 ? first : second;
    }
}
