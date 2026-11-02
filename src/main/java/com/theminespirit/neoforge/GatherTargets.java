package com.theminespirit.neoforge;

import com.theminespirit.core.forestry.ChopTuning;
import com.theminespirit.core.forestry.NotATree;
import com.theminespirit.core.forestry.TreeDetection;
import com.theminespirit.core.forestry.TreeShape;
import com.theminespirit.core.forestry.TreeSpecies;
import com.theminespirit.core.mining.GatherPolicy;
import com.theminespirit.core.mining.StoneSource;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

final class GatherTargets implements HarvestTargets {
    private final Level level;
    private final BlockPos center;
    private int radius;
    private final Set<Block> blocks;
    private BlockLocator.Search search;
    private final String refusal;
    private int slices;
    private final boolean settled;
    private int surfaceStone;
    private final @Nullable LevelTreeView treeView;
    private final boolean treeLogs;
    private int stumpsSkipped;

    private final @Nullable StageSite settlement;
    private int settlementSkipped;

    GatherTargets(Level level, BlockPos center, int radius, IdFilter filter, boolean settled) {
        this.level = level;
        this.settlement = level instanceof ServerLevel server && settled
                ? SettlementPlotStore.get(server)
                        .site()
                        .filter(site -> site.near(center.getX(), center.getZ()))
                        .orElse(null)
                : null;
        this.settled = settled;
        this.center = center;
        this.blocks = BlockSets.resolve(filter);
        this.treeLogs = exactLog(filter) != null;
        this.radius = treeLogs ? ChopTuning.gatherDistance(radius) : radius;
        this.search = BlockLocator.search(level, center, this.radius, blocks);
        this.treeView = treeLogs ? new LevelTreeView(level, blocks) : null;
        this.refusal = exactRefusal(filter);
    }

    @Override
    public List<BlockCoord> advance() {
        List<BlockCoord> allowed = scan();
        if (!allowed.isEmpty()) {
            return allowed;
        }
        return extendSearch() ? advance() : List.of();
    }

    private List<BlockCoord> scan() {
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
                if (plannedBySettlement(pos)) {
                    settlementSkipped++;
                    continue;
                }
                if (PlotGround.natural(level.getBlockState(pos)) && forbiddenBySettlement(pos)) {
                    settlementSkipped++;
                    continue;
                }
                BlockCoord cell = new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
                if (treeView != null && stump(cell)) {
                    stumpsSkipped++;
                    continue;
                }
                if (GatherPolicy.sourceCheck(id) instanceof GatherPolicy.Allowed) {
                    allowed.add(cell);
                }
            }
            if (!allowed.isEmpty()) {
                return allowed;
            }
        }
        return List.of();
    }

    private boolean extendSearch() {
        if (!treeLogs) {
            return false;
        }
        int next = ChopTuning.gatherDistance(Math.max(radius + 1, radius * 2));
        if (next <= radius) {
            return false;
        }
        radius = next;
        search = BlockLocator.search(level, center, radius, blocks);
        return true;
    }

    private boolean stump(BlockCoord cell) {
        TreeDetection detection = TreeShape.detect(cell, treeView);
        return detection instanceof NotATree notATree
                && (notATree.reason() == NotATree.Reason.NO_LEAVES || notATree.reason() == NotATree.Reason.NOT_A_TRUNK);
    }

    private static @Nullable TreeSpecies exactLog(IdFilter filter) {
        for (String pattern : filter.patterns()) {
            if (IdFilter.isPattern(pattern)) {
                continue;
            }
            String id = pattern.startsWith("minecraft:") ? pattern : "minecraft:" + pattern;
            if (TreeSpecies.ofLog(id).isPresent()) {
                return TreeSpecies.ofLog(id).orElseThrow();
            }
        }
        return null;
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
        if (refusal == null && treeLogs && stumpsSkipped > 0 && search.done() && surfaceStone == 0) {
            return "подходящие брёвна — только пни без кроны (" + stumpsSkipped
                    + "): нужен настоящий ствол с листвой, а не остаток дерева";
        }
        if (refusal == null && settlementSkipped > 0 && search.done() && surfaceStone == 0) {
            return "подходящие блоки только на участке поселения или в пещерах под ним (" + settlementSkipped
                    + "): там добывать нельзя";
        }
        return refusal == null && surfaceStone > 0 && search.done() ? StoneSource.REFUSAL : refusal;
    }

    private boolean plannedBySettlement(BlockPos pos) {
        StageSite site = settlement;
        return site != null && site.planCell(pos.getX(), pos.getY(), pos.getZ());
    }

    private boolean forbiddenBySettlement(BlockPos pos) {
        StageSite site = settlement;
        if (site == null) {
            return false;
        }
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()) - 1;
        return site.gatherForbidden(new BlockCoord(pos.getX(), pos.getY(), pos.getZ()), surface - pos.getY());
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
            if (GatherPolicy.sourceCheck(pattern) instanceof GatherPolicy.Refused refused) {
                return refused.reason();
            }
        }
        return null;
    }
}
