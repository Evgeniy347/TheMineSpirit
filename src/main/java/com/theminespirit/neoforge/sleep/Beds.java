package com.theminespirit.neoforge.sleep;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.neoforge.BlockLocator;
import com.theminespirit.neoforge.BlockSets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

final class Beds {
    private static final int UNLIMITED_SLICES = Integer.MAX_VALUE;
    private static final Set<Block> BED_BLOCKS = BlockSets.resolve(IdFilter.of(List.of("*_bed")));

    private Beds() {}

    static List<BlockCoord> find(Level level, BlockPos center, int radius) {
        BlockLocator.Search search = BlockLocator.search(level, center, radius, BED_BLOCKS);
        List<BlockCoord> beds = new ArrayList<>();
        while (!search.done()) {
            for (BlockPos pos : search.step(UNLIMITED_SLICES)) {
                beds.add(new BlockCoord(pos.getX(), pos.getY(), pos.getZ()));
            }
        }
        return beds;
    }

    static Set<BlockCoord> taken(Level level, List<BlockCoord> beds) {
        Set<BlockCoord> taken = new HashSet<>();
        for (BlockCoord bed : beds) {
            if (occupied(level, bed)) {
                taken.add(bed);
            }
        }
        return taken;
    }

    static boolean occupied(Level level, BlockCoord bed) {
        BlockState state = level.getBlockState(pos(bed));
        return state.getBlock() instanceof BedBlock && state.getValue(BedBlock.OCCUPIED);
    }

    static boolean isBed(Level level, BlockCoord bed) {
        return level.getBlockState(pos(bed)).getBlock() instanceof BedBlock;
    }

    static BlockPos pos(BlockCoord bed) {
        return new BlockPos(bed.x(), bed.y(), bed.z());
    }
}
