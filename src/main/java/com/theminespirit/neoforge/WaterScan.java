package com.theminespirit.neoforge;

import com.theminespirit.core.farming.Hydration;
import com.theminespirit.core.farming.WaterCells;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

final class WaterScan {
    private final Optional<BlockLocator.Search> search;
    private final WaterCells water = new WaterCells();
    private int slices;

    private WaterScan(Optional<BlockLocator.Search> search) {
        this.search = search;
    }

    static WaterScan around(Level level, BlockPos center, int radius) {
        return new WaterScan(
                Optional.of(BlockLocator.search(level, center, radius + Hydration.RADIUS, Set.of(Blocks.WATER))));
    }

    static WaterScan skipped() {
        return new WaterScan(Optional.empty());
    }

    void step(int maxSlices) {
        BlockLocator.Search active = search.orElseThrow(() -> new IllegalStateException(
                "Воду для этой работы не ищут: шаг скана воды вызывают только после проверки done()"));
        for (int i = 0; i < maxSlices && !active.done() && !water.exceeded(); i++) {
            slices++;
            for (BlockPos pos : active.step(1)) {
                water.add(new BlockCoord(pos.getX(), pos.getY(), pos.getZ()));
            }
        }
    }

    boolean done() {
        return search.map(active -> active.done() || water.exceeded()).orElse(true);
    }

    boolean exceeded() {
        return water.exceeded();
    }

    Hydration hydration() {
        if (!done()) {
            throw new IllegalStateException(
                    "Скан воды не закончен: увлажнение по неполному множеству дало бы ложное «сухо»");
        }
        return Hydration.of(search.isPresent() ? water.cells() : Set.of());
    }

    int slicesUsed() {
        return slices;
    }

    int maxSlicesPerTick() {
        return search.map(BlockLocator.Search::maxSlicesPerTick).orElse(0);
    }
}
