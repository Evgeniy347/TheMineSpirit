package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.movement.WaterCrossingPlanner;
import com.theminespirit.core.town.PlotProtection;
import com.theminespirit.neoforge.movement.CheapBlocks;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class DigOutBackfill {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final SpiritEntity spirit;
    private final Level level;
    private final BlockPlacer placer;
    private final boolean enabled;
    private int closed;
    private @Nullable BlockPos previous;
    private @Nullable String previousId;

    DigOutBackfill(SpiritEntity spirit, Level level, BlockPlacer placer, boolean enabled) {
        this.spirit = spirit;
        this.level = level;
        this.placer = placer;
        this.enabled = enabled;
    }

    void remember(BlockPos cell) {
        remember(cell, null);
    }

    void remember(BlockPos cell, @Nullable String blockId) {
        previous = cell;
        previousId = blockId;
    }

    void close() {
        if (!enabled || previous == null) {
            return;
        }
        closeCell(previous, previousId);
        closeCell(previous.above(), null);
        previous = null;
        previousId = null;
    }

    void report() {
        if (closed > 0) {
            LOGGER.info("[dig_out] {}: заделано {} клеток за спиной", spirit.displayName(), closed);
        }
    }

    static boolean passable(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty()
                && state.getFluidState().isEmpty();
    }

    static boolean water(Level level, BlockPos cell) {
        return level.getFluidState(cell).is(FluidTags.WATER);
    }

    private void closeCell(BlockPos cell, @Nullable String preferred) {
        if (!level.getBlockState(cell).isAir()) {
            return;
        }
        Map<String, Integer> counts = CheapBlocks.counts(spirit);
        Optional<String> blockId = preferred != null && counts.getOrDefault(preferred, 0) > 0
                ? Optional.of(preferred)
                : WaterCrossingPlanner.cheapestAvailable(counts);
        if (blockId.isEmpty()) {
            return;
        }
        String id = blockId.get();
        BlockPlacer writer = preferred == null ? placer : new BlockPlacer(spirit, PlotProtection.Ground.PLAN_BUILD);
        if (writer.begin(cell, BlockStates.stateFor(id), id) instanceof BlockPlacer.Started) {
            closed++;
        }
    }
}
