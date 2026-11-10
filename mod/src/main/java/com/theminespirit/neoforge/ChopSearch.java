package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.forestry.ChopTuning;
import com.theminespirit.core.forestry.Tree;
import com.theminespirit.core.forestry.TreeCandidates;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.ScanTuning;
import com.theminespirit.core.script.ChopTreeParams;
import com.theminespirit.core.town.SettlementPlot;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import com.theminespirit.neoforge.policy.OrchardTrees;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

final class ChopSearch {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SETTLEMENT_PROXIMITY = ChopTuning.MAX_SEARCH_RADIUS;
    private final SpiritEntity spirit;
    private final Set<Block> logBlocks;
    private final BlockCoord anchor;
    private final int radius;
    private int maxDistance;
    private final TreeCandidates candidates = new TreeCandidates(ChopTuning.SEARCH_MAX_LOGS);
    private BlockLocator.Search search;
    private BlockCoord center;
    private int restarts;
    private int nearestFar = -1;

    ChopSearch(SpiritEntity spirit, ChopTreeParams params, Set<Block> logBlocks) {
        this.spirit = spirit;
        this.logBlocks = logBlocks;
        this.anchor = anchorFor(spirit);
        this.radius = ChopTuning.gatherDistance(params.radius());
        this.maxDistance = radius;
        this.center = coords(spirit.blockPosition());
        this.search = newSearch(center);
    }

    private static BlockCoord anchorFor(SpiritEntity spirit) {
        BlockCoord start = coords(spirit.blockPosition());
        if (!(spirit.level() instanceof ServerLevel server)) {
            return start;
        }
        return SettlementPlotStore.get(server)
                .plot()
                .map(SettlementPlot::anchor)
                .filter(plot -> Math.abs(plot.x() - start.x()) <= SETTLEMENT_PROXIMITY
                        && Math.abs(plot.z() - start.z()) <= SETTLEMENT_PROXIMITY)
                .orElse(start);
    }

    Optional<BlockCoord> next() {
        locate();
        return candidates.nearestNear(anchor, maxDistance, spirit.getBlockX(), spirit.getBlockY(), spirit.getBlockZ());
    }

    boolean done() {
        return search.done();
    }

    boolean restart() {
        BlockCoord at = coords(spirit.blockPosition());
        if (restarts >= ChopTuning.SEARCH_RESTARTS_MAX || at.equals(center)) {
            return extend();
        }
        restarts++;
        center = at;
        search = newSearch(at);
        return true;
    }

    boolean extend() {
        int target = ChopTuning.extendTo(maxDistance, nearestFar);
        if (target <= maxDistance) {
            return false;
        }
        maxDistance = target;
        center = coords(spirit.blockPosition());
        search = newSearch(center);
        nearestFar = -1;
        return true;
    }

    void markKnown(Collection<BlockCoord> logs) {
        candidates.markKnown(logs);
    }

    int maxDistance() {
        return maxDistance;
    }

    int nearestFarDistance() {
        return nearestFar;
    }

    void logChosen(Tree found) {
        BlockCoord base = found.base();
        BlockPos pos = new BlockPos(base.x(), base.y(), base.z());
        double toSpirit = Math.sqrt(spirit.position().distanceToSqr(Vec3.atBottomCenterOf(pos)));
        LOGGER.info(
                "[chop] {} выбрал дерево {}: до духа {}, до поселения {}, чанк {}",
                spirit.getName().getString(),
                base,
                String.format(Locale.ROOT, "%.1f", toSpirit),
                String.format(Locale.ROOT, "%.1f", distanceToAnchor(base)),
                spirit.level().isLoaded(pos) ? "загружен" : "не загружен");
    }

    private double distanceToAnchor(BlockCoord cell) {
        long dx = (long) anchor.x() - cell.x();
        long dz = (long) anchor.z() - cell.z();
        return Math.sqrt((double) (dx * dx + dz * dz));
    }

    private void locate() {
        if (candidates.isFull()) {
            return;
        }
        for (BlockPos pos : search.step(ScanTuning.SLICES_PER_TICK)) {
            BlockCoord cell = new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
            if (OrchardTrees.planned(spirit.level(), cell)) {
                continue;
            }
            long dx = (long) anchor.x() - cell.x();
            long dz = (long) anchor.z() - cell.z();
            long squared = dx * dx + dz * dz;
            if (squared > (long) maxDistance * maxDistance) {
                noteFar(squared);
                continue;
            }
            candidates.offer(cell);
        }
    }

    private void noteFar(long squared) {
        int distance = (int) Math.ceil(Math.sqrt((double) squared));
        if (nearestFar < 0 || distance < nearestFar) {
            nearestFar = distance;
        }
    }

    private BlockLocator.Search newSearch(BlockCoord at) {
        int scan = ChopTuning.scanRadius(radius, maxDistance);
        return BlockLocator.search(spirit.level(), new BlockPos(at.x(), at.y(), at.z()), scan, Set.copyOf(logBlocks));
    }

    private static BlockCoord coords(BlockPos pos) {
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}
