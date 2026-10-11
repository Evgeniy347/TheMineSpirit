package com.theminespirit.neoforge;

import com.theminespirit.core.mining.MiningTuning;
import com.theminespirit.core.mining.Vein;
import com.theminespirit.core.mining.VeinWalker;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

final class VeinTargets implements HarvestTargets {
    private final BlockLocator.Search search;
    private final LevelOreView oreView;
    private final Set<BlockCoord> seen = new HashSet<>();
    private int slices;

    VeinTargets(Level level, BlockPos center, int radius, Set<Block> ores) {
        this.search = BlockLocator.search(level, center, radius, ores);
        this.oreView = new LevelOreView(level, ores);
    }

    @Override
    public List<BlockCoord> advance() {
        for (int i = 0; i < MAX_SLICES_PER_TICK && !search.done(); i++) {
            slices++;
            List<BlockPos> found = search.step(1);
            if (!found.isEmpty()) {
                return expand(found);
            }
        }
        return List.of();
    }

    @Override
    public boolean done() {
        return search.done();
    }

    @Override
    public int slicesUsed() {
        return slices;
    }

    private List<BlockCoord> expand(List<BlockPos> found) {
        List<BlockCoord> vein = new ArrayList<>();
        for (BlockPos pos : found) {
            BlockCoord start = new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
            if (!seen.add(start)) {
                continue;
            }
            Vein connected = VeinWalker.collect(start, oreView, MiningTuning.VEIN_MAX_BLOCKS);
            for (BlockCoord block : connected.blocks()) {
                seen.add(block);
                vein.add(block);
            }
        }
        return vein;
    }
}