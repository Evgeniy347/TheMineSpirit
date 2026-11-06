package com.theminespirit.neoforge;

import com.theminespirit.core.mining.MineColumn;
import com.theminespirit.core.mining.MineWalls;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

final class MineCells {

    private final Level level;
    private final Set<BlockCoord> walls;

    MineCells(Level level, Set<BlockCoord> walls) {
        this.level = Objects.requireNonNull(level, "Мир не может быть null: по нему читаются клетки шахты");
        this.walls = new HashSet<>(Objects.requireNonNull(walls, "Стенки не могут быть null: пустой набор — Set.of()"));
    }

    void removeWalls(Collection<BlockCoord> gone) {
        walls.removeAll(gone);
    }

    boolean open(BlockCoord cell) {
        BlockState state = level.getBlockState(toPos(cell));
        return (state.isAir() || state.canBeReplaced()) && state.getFluidState().isEmpty();
    }

    boolean fluid(BlockCoord cell) {
        return !level.getBlockState(toPos(cell)).getFluidState().isEmpty();
    }

    List<BlockCoord> wallsOf(MineColumn column) {
        return MineWalls.of(column, walls);
    }

    private static BlockPos toPos(BlockCoord cell) {
        return new BlockPos(cell.x(), cell.y(), cell.z());
    }
}
