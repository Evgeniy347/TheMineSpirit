package com.theminespirit.neoforge.crafting;

import com.theminespirit.core.crafting.CraftTuning;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

public final class StationPlacer {
    private StationPlacer() {
    }

    public static Optional<BlockPos> place(SpiritEntity spirit, StationType type) {
        return place(spirit, type, spirit.blockPosition());
    }

    public static Optional<BlockPos> place(SpiritEntity spirit, StationType type, BlockPos origin) {
        Level level = spirit.level();
        if (spirit.getInventory().countItem(type.item()) < 1) {
            return Optional.empty();
        }
        for (BlockPos cell : cellsAround(origin)) {
            Direction toward = Direction.getNearest(spirit.getX() - (cell.getX() + 0.5), 0,
                    spirit.getZ() - (cell.getZ() + 0.5));
            BlockState state = type.placedState(toward);
            if (isFree(level, cell, state)) {
                spirit.getInventory().removeItemType(type.item(), 1);
                level.setBlock(cell, state, Block.UPDATE_ALL);
                return Optional.of(cell);
            }
        }
        return Optional.empty();
    }

    private static List<BlockPos> cellsAround(BlockPos origin) {
        int reach = CraftTuning.PLACE_SEARCH_RADIUS;
        List<BlockPos> cells = new ArrayList<>();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                if (dx != 0 || dz != 0) {
                    cells.add(origin.offset(dx, 0, dz));
                }
            }
        }
        cells.sort(Comparator.comparingDouble((BlockPos cell) -> cell.distSqr(origin)).thenComparing(Comparator.naturalOrder()));
        return cells;
    }

    private static boolean isFree(Level level, BlockPos cell, BlockState state) {
        BlockPos floor = cell.below();
        return level.getBlockState(cell).isAir()
                && level.getBlockState(cell.above()).isAir()
                && level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
                && level.isUnobstructed(state, cell, CollisionContext.empty());
    }
}
