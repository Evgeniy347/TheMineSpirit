package com.theminespirit.neoforge;

import com.theminespirit.core.mining.TargetBuffer;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

final class GatherCells {
    private final Level level;
    private final SoilCells soil;
    private final int surfaceY;

    GatherCells(Level level, int surfaceY) {
        this.level = level;
        this.soil = new SoilCells(level);
        this.surfaceY = surfaceY;
    }

    List<BlockCoord> shallow(List<BlockCoord> candidates, TargetBuffer buffer) {
        List<BlockCoord> kept = new ArrayList<>();
        for (BlockCoord candidate : candidates) {
            BlockState state = level.getBlockState(toPos(candidate));
            boolean loose = state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL);
            boolean refused =
                    state.is(BlockTags.DIRT) ? !soil.takeable(candidate) : loose && candidate.y() < surfaceY - 1;
            if (refused) {
                buffer.remove(candidate);
            } else {
                kept.add(candidate);
            }
        }
        return kept;
    }

    boolean exposed(BlockCoord cell) {
        BlockPos pos = toPos(cell);
        for (Direction side : Direction.values()) {
            BlockState neighbour = level.getBlockState(pos.relative(side));
            if (neighbour.isAir() || neighbour.canBeReplaced()) {
                return true;
            }
        }
        return false;
    }

    private static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }
}
