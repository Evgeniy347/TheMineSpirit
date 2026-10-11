package com.theminespirit.neoforge;

import com.theminespirit.core.mining.GatherPolicy;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

final class GatherTargets implements HarvestTargets {
    private final Level level;
    private final BlockLocator.Search search;
    private final String refusal;
    private int slices;

    GatherTargets(Level level, BlockPos center, int radius, IdFilter filter) {
        this.level = level;
        this.search = BlockLocator.search(level, center, radius, BlockSets.resolve(filter));
        this.refusal = exactRefusal(filter);
    }

    @Override
    public List<BlockCoord> advance() {
        for (int i = 0; i < MAX_SLICES_PER_TICK && !search.done(); i++) {
            slices++;
            List<BlockPos> found = search.step(1);
            List<BlockCoord> allowed = new ArrayList<>();
            for (BlockPos pos : found) {
                if (GatherPolicy.check(blockId(pos)) instanceof GatherPolicy.Allowed) {
                    allowed.add(new BlockCoord(pos.getX(), pos.getY(), pos.getZ()));
                }
            }
            if (!allowed.isEmpty()) {
                return allowed;
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

    @Override
    public String refusalReason() {
        return refusal;
    }

    private String blockId(BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        return BuiltInRegistries.BLOCK.getKey(block).toString();
    }

    private static String exactRefusal(IdFilter filter) {
        for (String pattern : filter.patterns()) {
            if (pattern.indexOf('*') >= 0) {
                continue;
            }
            if (GatherPolicy.check(pattern) instanceof GatherPolicy.Refused refused) {
                return refused.reason();
            }
        }
        return null;
    }
}