package com.theminespirit.neoforge.crafting;

import com.theminespirit.core.script.CraftParams;
import com.theminespirit.core.town.PlotProtection;
import com.theminespirit.neoforge.BlockPlacer;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.PlacementJournal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;

final class CraftPlacement {
    private static final int PLACE_REACH = 3;
    private static final int PLACE_PASSES = 3;

    private CraftPlacement() {}

    static Optional<String> placeBlocks(SpiritEntity spirit, CraftParams params, int crafted) {
        Optional<Item> item = ItemIds.item(ItemIds.normalize(params.item()));
        if (item.isEmpty() || !(item.get() instanceof BlockItem blockItem)) {
            return Optional.of("place: '" + params.item() + "' не блок — ставить нечего");
        }
        int left = Math.min(crafted, spirit.getInventory().countItem(blockItem));
        BlockPlacer placer = new BlockPlacer(spirit, PlotProtection.Ground.PLAN_BUILD);
        BlockState state = blockItem.getBlock().defaultBlockState();
        String id = "minecraft:" + ItemIds.normalize(params.item());
        BlockPos origin = spirit.blockPosition();
        List<BlockPos> cells = new ArrayList<>();
        for (int dx = -PLACE_REACH; dx <= PLACE_REACH; dx++) {
            for (int dz = -PLACE_REACH; dz <= PLACE_REACH; dz++) {
                if (dx != 0 || dz != 0) {
                    cells.add(origin.offset(dx, 0, dz));
                }
            }
        }
        cells.sort(Comparator.comparingDouble((BlockPos cell) -> cell.distSqr(origin))
                .thenComparing(Comparator.naturalOrder()));
        Level level = spirit.level();
        while (left > 0) {
            Optional<BlockPos> placed = Optional.empty();
            for (int pass = 0; pass < PLACE_PASSES && placed.isEmpty(); pass++) {
                placed = placeOnce(spirit, level, placer, cells, state, id, pass, left);
            }
            if (placed.isEmpty()) {
                break;
            }
            left--;
        }
        if (left > 0) {
            return Optional.of("негде поставить '" + params.item() + "': осталось " + left
                    + ", вокруг духа нет свободной клетки на твёрдом полу");
        }
        return Optional.empty();
    }

    private static Optional<BlockPos> placeOnce(
            SpiritEntity spirit,
            Level level,
            BlockPlacer placer,
            List<BlockPos> cells,
            BlockState state,
            String id,
            int pass,
            int left) {
        for (BlockPos cell : cells) {
            Optional<BlockState> pair = ChestPairing.paired(level, cell, state);
            boolean wanted = pass == 0
                    ? pair.isPresent()
                    : pair.isEmpty() && (pass == 2 || (left > 1 && partnerFree(level, cells, cell, state)));
            if (wanted
                    && freeCell(level, cell, state, pair.isPresent())
                    && placer.begin(cell, pair.orElse(state), id) instanceof BlockPlacer.Started) {
                PlacementJournal.placed(
                        spirit, id, cell, "скрафчено с place" + (pair.isPresent() ? ", вторая половина двойного" : ""));
                return Optional.of(cell);
            }
        }
        return Optional.empty();
    }

    private static boolean partnerFree(Level level, List<BlockPos> cells, BlockPos cell, BlockState state) {
        if (!(state.getBlock() instanceof ChestBlock)) {
            return false;
        }
        Direction facing = state.getValue(ChestBlock.FACING);
        for (BlockPos partner :
                List.of(cell.relative(facing.getClockWise()), cell.relative(facing.getCounterClockWise()))) {
            if (cells.contains(partner) && freeCell(level, partner, state, false)) {
                return true;
            }
        }
        return false;
    }

    private static boolean freeCell(Level level, BlockPos cell, BlockState state, boolean paired) {
        BlockPos floor = cell.below();
        if (!level.getBlockState(cell).isAir()
                || !level.getBlockState(cell.above()).isAir()
                || !level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
            return false;
        }
        int neighbours = 0;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (level.getBlockState(cell.relative(side)).is(state.getBlock())) {
                neighbours++;
            }
        }
        return neighbours == (paired ? 1 : 0);
    }
}
