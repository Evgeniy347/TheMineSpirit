package com.theminespirit.neoforge.movement;

import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.movement.Passage;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.LevelSafetyView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.Nullable;

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
        Passage.Cell passage = passage(coord, state);
        if (passage != null && passage.routePassable()) {
            return Cell.PASSABLE;
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

    private static Passage.@Nullable Cell passage(BlockCoord coord, BlockState state) {
        if (state.getBlock() instanceof DoorBlock door) {
            if (!door.type().canOpenByHand()) {
                return null;
            }
            return new Passage.Cell(coord, Passage.Kind.DOOR, state.getValue(DoorBlock.OPEN));
        }
        if (state.getBlock() instanceof FenceGateBlock) {
            return new Passage.Cell(coord, Passage.Kind.FENCE_GATE, state.getValue(BlockStateProperties.OPEN));
        }
        return null;
    }

    private boolean tallBelow(BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).getCollisionShape(level, below).max(Direction.Axis.Y) > FULL_BLOCK;
    }
}
