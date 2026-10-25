package com.theminespirit.neoforge.movement;

import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.LevelSafetyView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class NetworkLevelView implements SafetyView {
    // mc: шаг тела игры — 0,6 блока; блок ниже 3/16 (ковёр 1/16, люк 3/16) поиск пути проходит как пол, без шага вверх.
    private static final double LOW_BLOCK = 0.1875;
    private static final double FULL_BLOCK = 1.0;

    private final Level level;
    private final LevelSafetyView cells;

    public NetworkLevelView(Level level) {
        this.level = level;
        this.cells = new LevelSafetyView(level);
    }

    @Override
    public Cell cellAt(BlockCoord coord) {
        BlockPos pos = new BlockPos(coord.x(), coord.y(), coord.z());
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock) {
            return state.getValue(DoorBlock.OPEN) ? Cell.PASSABLE : Cell.SOLID;
        }
        Cell cell = cells.cellAt(coord);
        if (cell == Cell.PASSABLE && tallBelow(pos)) {
            return Cell.SOLID;
        }
        if (cell == Cell.SOLID && state.getCollisionShape(level, pos).max(Direction.Axis.Y) <= LOW_BLOCK) {
            return Cell.PASSABLE;
        }
        return cell;
    }

    private boolean tallBelow(BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).getCollisionShape(level, below).max(Direction.Axis.Y) > FULL_BLOCK;
    }
}
