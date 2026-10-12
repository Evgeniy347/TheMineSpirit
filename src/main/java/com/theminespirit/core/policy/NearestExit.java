package com.theminespirit.core.policy;

import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

final class NearestExit {
    private static final int[] LEVELS = {0, 1};

    private NearestExit() {
    }

    static Optional<BlockCoord> find(LocalSurroundings surroundings, SafetyView standView) {
        BlockCoord feet = surroundings.feet();
        List<BlockCoord> candidates = new ArrayList<>();
        for (int dy : LEVELS) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if ((dx != 0 || dz != 0) && (dy == 0 || surroundings.cellAt(0, 1, 0) != Cell.SOLID)) {
                        candidates.add(new BlockCoord(feet.x() + dx, feet.y() + dy, feet.z() + dz));
                    }
                }
            }
        }
        candidates.sort(Comparator.comparingInt((BlockCoord c) -> squaredDistance(feet, c)).thenComparing(Comparator.naturalOrder()));
        for (BlockCoord candidate : candidates) {
            BlockCoord head = new BlockCoord(candidate.x(), candidate.y() + 1, candidate.z());
            if (surroundings.cellAt(candidate) == Cell.PASSABLE && surroundings.cellAt(head) == Cell.PASSABLE
                    && MiningSafety.canStand(candidate, standView)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static int squaredDistance(BlockCoord a, BlockCoord b) {
        int dx = a.x() - b.x();
        int dy = a.y() - b.y();
        int dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
