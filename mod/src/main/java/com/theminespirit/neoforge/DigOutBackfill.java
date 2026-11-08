package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.movement.WaterCrossingPlanner;
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
    private final @Nullable DigOutRestore restore;
    private int closed;
    private @Nullable BlockPos previous;

    DigOutBackfill(
            SpiritEntity spirit, Level level, BlockPlacer placer, boolean enabled, @Nullable DigOutRestore restore) {
        this.spirit = spirit;
        this.level = level;
        this.placer = placer;
        this.enabled = enabled;
        this.restore = restore;
    }

    void remember(BlockPos cell) {
        previous = cell;
    }

    void close() {
        if (!enabled || previous == null) {
            return;
        }
        closeCell(previous);
        closeCell(previous.above());
        previous = null;
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

    private void closeCell(BlockPos cell) {
        if (!level.getBlockState(cell).isAir()) {
            return;
        }
        if (restore != null && restore.pending(cell)) {
            return;
        }
        Map<String, Integer> counts = CheapBlocks.counts(spirit);
        Optional<String> blockId = WaterCrossingPlanner.cheapestAvailable(counts);
        if (blockId.isEmpty()) {
            return;
        }
        String id = blockId.get();
        if (placer.begin(cell, BlockStates.stateFor(id), id) instanceof BlockPlacer.Started) {
            closed++;
        }
    }
}
