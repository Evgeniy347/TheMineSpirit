package com.theminespirit.neoforge;

import com.theminespirit.core.board.SettlementLayout;
import com.theminespirit.core.board.SettlementPlot;
import com.theminespirit.core.mining.GatherPolicy;
import com.theminespirit.core.mining.StoneSource;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

final class GatherTargets implements HarvestTargets {
    private final Level level;
    private final BlockLocator.Search search;
    private final String refusal;
    private int slices;
    private final boolean settled;
    private int surfaceStone;

    private final @Nullable BlockCoord settlement;
    private int settlementSkipped;

    GatherTargets(Level level, BlockPos center, int radius, IdFilter filter, boolean settled) {
        this.level = level;
        // Запрет добычи на участке и в пещерах под ним — только духу этого поселения (дом есть, сам у участка): сцены
        // GameTest в стороне от чужого участка не теряют свою землю.
        this.settlement = level instanceof ServerLevel server && settled
                ? SettlementPlotStore.get(server)
                        .plot()
                        .filter(plot -> plot.contains(new BlockCoord(center.getX(), center.getY(), center.getZ())))
                        .map(SettlementPlot::anchor)
                        .orElse(null)
                : null;
        this.settled = settled;
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
                String id = blockId(pos);
                if (!StoneSource.allowed(id, level.canSeeSky(pos.above()), settled)) {
                    surfaceStone++;
                    continue;
                }
                if (forbiddenBySettlement(pos)) {
                    settlementSkipped++;
                    continue;
                }
                if (GatherPolicy.check(id) instanceof GatherPolicy.Allowed) {
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
    public int maxSlicesPerTick() {
        return search.maxSlicesPerTick();
    }

    @Override
    public String refusalReason() {
        if (refusal == null && settlementSkipped > 0 && search.done() && surfaceStone == 0) {
            return "подходящие блоки только на участке поселения или в пещерах под ним (" + settlementSkipped
                    + "): там добывать нельзя";
        }
        return refusal == null && surfaceStone > 0 && search.done() ? StoneSource.REFUSAL : refusal;
    }

    private boolean forbiddenBySettlement(BlockPos pos) {
        BlockCoord anchor = settlement;
        if (anchor == null) {
            return false;
        }
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()) - 1;
        return SettlementLayout.gatherForbidden(
                anchor, new BlockCoord(pos.getX(), pos.getY(), pos.getZ()), surface - pos.getY());
    }

    private String blockId(BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        return BuiltInRegistries.BLOCK.getKey(block).toString();
    }

    private static String exactRefusal(IdFilter filter) {
        for (String pattern : filter.patterns()) {
            if (IdFilter.isPattern(pattern)) {
                continue;
            }
            if (GatherPolicy.check(pattern) instanceof GatherPolicy.Refused refused) {
                return refused.reason();
            }
        }
        return null;
    }
}
