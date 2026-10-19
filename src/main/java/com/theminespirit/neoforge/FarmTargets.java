package com.theminespirit.neoforge;

import com.theminespirit.core.farming.CropKind;
import com.theminespirit.core.farming.FarmJob;
import com.theminespirit.core.farming.HarvestMethod;
import com.theminespirit.core.farming.Hydration;
import com.theminespirit.core.farming.WaterCells;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;

public final class FarmTargets implements HarvestTargets {
    private static final Set<Block> TILLABLE = Set.of(Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.DIRT_PATH);

    private final Level level;
    private final Optional<BlockLocator.Search> search;
    private final List<BlockCoord> plotGrounds;
    private final WaterScan water;
    private final int groundShift;
    private Hydration hydration;
    private int slices;
    private int plotCursor;

    public FarmTargets(Level level, BlockPos center, int radius, FarmJob job, CropKind crop) {
        this.level = level;
        this.search = Optional.of(BlockLocator.search(level, center, radius, blocksFor(job, crop)));
        this.plotGrounds = List.of();
        this.water = job == FarmJob.TILL ? WaterScan.around(level, center, radius) : WaterScan.skipped();
        this.groundShift = job == FarmJob.HARVEST || job == FarmJob.BONE_MEAL ? 1 : 0;
    }

    public FarmTargets(Level level, BlockPos center, int radius, List<BlockCoord> plotGrounds, FarmJob job) {
        this.level = level;
        this.search = Optional.empty();
        this.plotGrounds = List.copyOf(plotGrounds);
        this.water = job == FarmJob.TILL ? WaterScan.around(level, center, radius) : WaterScan.skipped();
        this.groundShift = 0;
    }

    @Override
    public List<BlockCoord> advance() {
        if (!water.done()) {
            water.step(MAX_SLICES_PER_TICK);
            return List.of();
        }
        if (search.isEmpty()) {
            int to = Math.min(plotGrounds.size(), plotCursor + MAX_SLICES_PER_TICK);
            List<BlockCoord> portion = plotGrounds.subList(plotCursor, to);
            slices += portion.size();
            plotCursor = to;
            return portion;
        }
        BlockLocator.Search active = search.orElseThrow();
        for (int i = 0; i < MAX_SLICES_PER_TICK && !active.done(); i++) {
            slices++;
            List<BlockPos> found = active.step(1);
            if (!found.isEmpty()) {
                return groundsOf(found);
            }
        }
        return List.of();
    }

    @Override
    public boolean done() {
        if (water.exceeded()) {
            return true;
        }
        if (water.done()) {
            return search.map(BlockLocator.Search::done).orElse(plotCursor >= plotGrounds.size());
        }
        return false;
    }

    @Override
    public int slicesUsed() {
        return slices + water.slicesUsed();
    }

    @Override
    public int maxSlicesPerTick() {
        return Math.max(search.map(BlockLocator.Search::maxSlicesPerTick).orElse(0), water.maxSlicesPerTick());
    }

    @Override
    public String refusalReason() {
        return water.exceeded() ? WaterCells.REFUSAL : null;
    }

    @Override
    public Hydration hydration() {
        if (hydration == null) {
            hydration = water.hydration();
        }
        return hydration;
    }

    private List<BlockCoord> groundsOf(List<BlockPos> found) {
        List<BlockCoord> grounds = new ArrayList<>(found.size());
        for (BlockPos pos : found) {
            BlockState state = level.getBlockState(pos);
            Optional<CropKind> kind = CropKind.ofBlock(idOf(state));
            if (kind.isPresent() && kind.get().hasSupport()) {
                BlockPos support = supportOf(pos, state);
                grounds.add(new BlockCoord(support.getX(), support.getY(), support.getZ()));
                continue;
            }
            BlockPos base = pos;
            if (groundShift == 1 && kind.isPresent() && kind.get().harvestMethod() == HarvestMethod.BREAK_COLUMN) {
                while (true) {
                    BlockPos below = base.below();
                    Optional<CropKind> belowKind = CropKind.ofBlock(idOf(level.getBlockState(below)));
                    if (belowKind.isPresent() && belowKind.get() == kind.get()) {
                        base = below;
                    } else {
                        break;
                    }
                }
            }
            grounds.add(new BlockCoord(base.getX(), base.getY() - groundShift, base.getZ()));
        }
        return grounds.stream().distinct().toList();
    }

    private static String idOf(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    private static BlockPos supportOf(BlockPos pos, BlockState state) {
        Property<?> facing = state.getBlock().getStateDefinition().getProperty("facing");
        if (facing instanceof DirectionProperty direction) {
            return pos.relative(state.getValue(direction));
        }
        throw new IllegalStateException("У культуры на опоре " + idOf(state) + " нет свойства facing: "
                + "без него не найти ствол, на котором она растёт, проверь id в CropKind");
    }

    private static Set<Block> blocksFor(FarmJob job, CropKind crop) {
        return switch (job) {
            case TILL -> TILLABLE;
            case PLANT -> crop.hasSupport() ? Set.of(CropRegistry.supportBlock(crop)) : Set.of(Blocks.FARMLAND);
            case HARVEST, BONE_MEAL -> CropRegistry.blocks(crop);
        };
    }
}
