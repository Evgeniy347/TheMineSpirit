package com.theminespirit.neoforge.intellect;

import com.theminespirit.core.intellect.WorkStations;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.processing.ProcessingRules;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.neoforge.BlockLocator;
import com.theminespirit.neoforge.BlockSets;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

public final class WorkStationScanner {
    private static final int UNLIMITED_SLICES = Integer.MAX_VALUE;
    private static final Set<Block> SMELT_BLOCKS =
            Set.of(Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER, Blocks.CAMPFIRE);

    private WorkStationScanner() {}

    public static WorkStations read(ServerLevel level, Optional<Point> home, List<SpiritEntity> members) {
        if (level == null || home == null || members == null) {
            throw new IllegalArgumentException(
                    "Станции поселения без уровня, дома или духов не читаются: снимок зависит от всех сразу");
        }
        Optional<BlockPos> center = centerOf(home, members);
        if (center.isEmpty()) {
            return WorkStations.NONE;
        }
        int radius = ProcessingRules.loadDefault().stationRadius();
        Set<Block> interesting = new HashSet<>(SMELT_BLOCKS);
        interesting.add(Blocks.CRAFTING_TABLE);
        interesting.addAll(BlockSets.resolve(IdFilter.of(List.of("*_bed"))));
        BlockLocator.Search search = BlockLocator.search(level, center.get(), radius, Set.copyOf(interesting));
        int smeltStations = 0;
        int tables = 0;
        int freeBeds = 0;
        while (!search.done()) {
            for (BlockPos pos : search.step(UNLIMITED_SLICES)) {
                BlockState state = level.getBlockState(pos);
                Block block = state.getBlock();
                if (SMELT_BLOCKS.contains(block)) {
                    smeltStations++;
                } else if (block.equals(Blocks.CRAFTING_TABLE)) {
                    tables++;
                } else if (freeBed(state)) {
                    freeBeds++;
                }
            }
        }
        return new WorkStations(
                freeBeds, smeltStations, tables, count(members, Items.FURNACE), count(members, Items.COBBLESTONE));
    }

    private static boolean freeBed(BlockState state) {
        return state.getBlock() instanceof BedBlock
                && state.getValue(BedBlock.PART) == BedPart.HEAD
                && !state.getValue(BedBlock.OCCUPIED);
    }

    private static int count(List<SpiritEntity> members, Item item) {
        int total = 0;
        for (SpiritEntity spirit : members) {
            if (!spirit.isRemoved()) {
                total += spirit.getInventory().countItem(item);
            }
        }
        return total;
    }

    private static Optional<BlockPos> centerOf(Optional<Point> home, List<SpiritEntity> members) {
        if (home.isPresent()) {
            Point point = home.get();
            return Optional.of(new BlockPos(
                    (int) Math.floor(point.x()), (int) Math.floor(point.y()), (int) Math.floor(point.z())));
        }
        for (SpiritEntity spirit : members) {
            if (!spirit.isRemoved()) {
                return Optional.of(spirit.blockPosition());
            }
        }
        return Optional.empty();
    }
}
