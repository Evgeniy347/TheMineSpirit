package com.theminespirit.neoforge;

import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicActions;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

final class SchematicWild {
    private SchematicWild() {}

    static boolean log(Level level, Set<BlockCoord> schemeCells, BlockCoord cell) {
        BlockPos pos = SchematicSite.toPos(cell);
        boolean placed = level instanceof ServerLevel server
                && PlacedBlocksData.get(server).isMarked(pos);
        return !schemeCells.contains(cell)
                && !placed
                && level.getBlockState(pos).is(BlockTags.LOGS);
    }

    static List<BlockCoord> clearCells(Level level, Set<BlockCoord> cells, List<Schematic.PlacedEntry> entries) {
        aboveAction(level, cells, entries);
        return new ArrayList<>(cells);
    }

    static void aboveAction(Level level, Set<BlockCoord> cells, List<Schematic.PlacedEntry> entries) {
        for (Schematic.PlacedEntry entry : entries) {
            if (!SchematicActions.isActionPlaced(entry.block())) {
                continue;
            }
            BlockCoord above = new BlockCoord(
                    entry.pos().x(), entry.pos().y() + 1, entry.pos().z());
            BlockPos pos = SchematicSite.toPos(above);
            if (weed(level, pos)) {
                cells.add(above);
            }
        }
    }

    static @Nullable BlockCoord entryWeed(Level level, BlockBreaker breaker, Schematic.PlacedEntry entry) {
        BlockCoord cell = entry.pos();
        if (SchematicActions.isTilled(entry.block())) {
            return weedAbove(level, breaker, cell);
        }
        return SchematicActions.isWalkway(entry.block()) ? weedCell(level, breaker, cell) : null;
    }

    static @Nullable BlockCoord weedAbove(Level level, BlockBreaker breaker, BlockCoord cell) {
        BlockCoord above = new BlockCoord(cell.x(), cell.y() + 1, cell.z());
        return weedAt(level, breaker, above);
    }

    private static @Nullable BlockCoord weedCell(Level level, BlockBreaker breaker, BlockCoord cell) {
        return weedAt(level, breaker, cell);
    }

    private static @Nullable BlockCoord weedAt(Level level, BlockBreaker breaker, BlockCoord cell) {
        BlockPos pos = SchematicSite.toPos(cell);
        if (!weed(level, pos)) {
            return null;
        }
        return breaker.begin(pos, BlockBreaker.Intent.DEMOLISH, false) instanceof BlockBreaker.Started ? cell : null;
    }

    private static boolean weed(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir()
                && state.canBeReplaced()
                && state.getFluidState().isEmpty()
                && state.getDestroySpeed(level, pos) == 0.0F;
    }
}
