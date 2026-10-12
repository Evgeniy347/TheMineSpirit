package com.theminespirit.neoforge;

import com.theminespirit.core.farming.CropKind;
import com.theminespirit.core.farming.FarmJob;
import com.theminespirit.core.farming.Hydration;
import com.theminespirit.core.farming.WaterCells;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public final class FarmTargets implements HarvestTargets {
    private static final Set<Block> TILLABLE = Set.of(Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.DIRT_PATH);

    private final BlockLocator.Search search;
    private final WaterScan water;
    private final int groundShift;
    private Hydration hydration;
    private int slices;

    public FarmTargets(Level level, BlockPos center, int radius, FarmJob job, CropKind crop) {
        this.search = BlockLocator.search(level, center, radius, blocksFor(job, crop));
        this.water = job == FarmJob.TILL ? WaterScan.around(level, center, radius) : WaterScan.skipped();
        this.groundShift = job == FarmJob.HARVEST || job == FarmJob.BONE_MEAL ? 1 : 0;
    }

    @Override
    public List<BlockCoord> advance() {
        if (!water.done()) {
            water.step(MAX_SLICES_PER_TICK);
            return List.of();
        }
        for (int i = 0; i < MAX_SLICES_PER_TICK && !search.done(); i++) {
            slices++;
            List<BlockPos> found = search.step(1);
            if (!found.isEmpty()) {
                return groundsOf(found);
            }
        }
        return List.of();
    }

    @Override
    public boolean done() {
        return water.exceeded() || (water.done() && search.done());
    }

    @Override
    public int slicesUsed() {
        return slices + water.slicesUsed();
    }

    @Override
    public int maxSlicesPerTick() {
        return Math.max(search.maxSlicesPerTick(), water.maxSlicesPerTick());
    }

    @Override
    public String refusalReason() {
        return water.exceeded() ? WaterCells.REFUSAL : null;
    }

    public Hydration hydration() {
        if (hydration == null) {
            hydration = water.hydration();
        }
        return hydration;
    }

    private List<BlockCoord> groundsOf(List<BlockPos> found) {
        List<BlockCoord> grounds = new ArrayList<>(found.size());
        for (BlockPos pos : found) {
            grounds.add(new BlockCoord(pos.getX(), pos.getY() - groundShift, pos.getZ()));
        }
        return grounds;
    }

    private static Set<Block> blocksFor(FarmJob job, CropKind crop) {
        return switch (job) {
            case TILL -> TILLABLE;
            case PLANT -> Set.of(Blocks.FARMLAND);
            case HARVEST, BONE_MEAL -> CropRegistry.blocks(crop);
        };
    }
}
