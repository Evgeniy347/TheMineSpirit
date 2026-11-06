package com.theminespirit.neoforge;

import com.theminespirit.core.forestry.ChopTuning;
import com.theminespirit.core.forestry.NotATree;
import com.theminespirit.core.forestry.Tree;
import com.theminespirit.core.forestry.TreeCandidates;
import com.theminespirit.core.forestry.TreeDetection;
import com.theminespirit.core.forestry.TreeShape;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.ScanTuning;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

final class TreeLocateSource implements LocateSource {
    private final BlockLocator.Search search;
    private final TreeCandidates candidates = new TreeCandidates(ChopTuning.SEARCH_MAX_LOGS);
    private final LevelTreeView view;
    private final int fromX;
    private final int fromY;
    private final int fromZ;

    TreeLocateSource(Level level, BlockPos center, int radius, Set<Block> logs) {
        this.search = BlockLocator.search(level, center, radius, logs);
        this.view = new LevelTreeView(level, logs);
        this.fromX = center.getX();
        this.fromY = center.getY();
        this.fromZ = center.getZ();
    }

    @Override
    public List<BlockCoord> advance() {
        if (!candidates.isFull()) {
            for (BlockPos pos : search.step(ScanTuning.SLICES_PER_TICK)) {
                candidates.offer(new BlockCoord(pos.getX(), pos.getY(), pos.getZ()));
            }
        }
        return verifyNearest();
    }

    @Override
    public boolean done() {
        return search.done() && candidates.liveCount() == 0;
    }

    @Override
    public int examinedPositions() {
        return search.examinedPositions();
    }

    private List<BlockCoord> verifyNearest() {
        Optional<BlockCoord> start = candidates.nearest(fromX, fromY, fromZ);
        if (start.isEmpty()) {
            return List.of();
        }
        if (!view.isLog(start.get())) {
            candidates.markKnown(List.of(start.get()));
            return List.of();
        }
        TreeDetection detection = TreeShape.detect(start.get(), view);
        if (detection instanceof NotATree rejected) {
            candidates.markKnown(rejected.logs());
            return List.of();
        }
        Tree tree = (Tree) detection;
        candidates.markKnown(tree.logs());
        return List.of(tree.base());
    }
}
