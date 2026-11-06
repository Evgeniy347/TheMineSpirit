package com.theminespirit.neoforge.inventory;

import com.theminespirit.core.script.ContainerTarget;
import com.theminespirit.neoforge.BlockLocator;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

final class ContainerFinder {
    private static final int SLICES_PER_TICK = 4;

    private final Level level;
    private final BlockPos center;
    private final ContainerTarget target;
    private final BlockLocator.Search search;
    private final List<BlockPos> candidates = new ArrayList<>();
    private boolean resolved;
    private BlockPos found;
    private String refusal;

    ContainerFinder(Level level, BlockPos center, ContainerTarget target, int radius) {
        this.level = level;
        this.center = center;
        this.target = target;
        if (target instanceof ContainerTarget.Nearest) {
            this.search = BlockLocator.search(level, center, radius, ContainerAccess.allowedBlocks());
        } else {
            this.search = null;
        }
    }

    boolean tick() {
        if (resolved) {
            return true;
        }
        if (target instanceof ContainerTarget.Position position) {
            found = new BlockPos(position.x(), position.y(), position.z());
            resolved = true;
            return true;
        }
        candidates.addAll(search.step(SLICES_PER_TICK));
        if (!search.done()) {
            return false;
        }
        resolved = true;
        double best = Double.MAX_VALUE;
        for (BlockPos candidate : candidates) {
            double distance = candidate.distSqr(center);
            if (distance < best) {
                best = distance;
                found = candidate;
            }
        }
        if (found == null) {
            refusal = "рядом нет допустимого контейнера: годятся сундуки (в том числе двойные), бочки "
                    + "и шалкер-боксы, а печи, воронки, раздатчики и эндер-сундук запрещены";
        }
        return true;
    }

    BlockPos found() {
        return found;
    }

    String refusal() {
        return refusal;
    }

    int maxSlicesPerTick() {
        return search == null ? 0 : search.maxSlicesPerTick();
    }

    Level level() {
        return level;
    }
}
