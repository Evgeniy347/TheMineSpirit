package com.theminespirit.neoforge;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

final class ClearAnchor {
    static final int MAX_RISE = 24;
    static final int BELOW = 2;

    record Stand(int x, int y, int z) {
        BlockCoord coord() {
            return new BlockCoord(x, y, z);
        }
    }

    private ClearAnchor() {}

    static @Nullable Stand best(
            List<BlockCoord> remaining, List<Stand> stands, BlockCoord from, int reach, Set<BlockCoord> tried) {
        Stand best = null;
        int bestScore = 0;
        double bestDistance = Double.MAX_VALUE;
        for (Stand stand : stands) {
            if (tried.contains(stand.coord()) || stand.y() < from.y() - BELOW) {
                continue;
            }
            int score = 0;
            for (BlockCoord cell : remaining) {
                int dx = Math.abs(cell.x() - stand.x());
                int dz = Math.abs(cell.z() - stand.z());
                int dy = cell.y() - stand.y();
                if (dx <= reach && dz <= reach && dy >= -BELOW && dy <= MAX_RISE) {
                    score++;
                }
            }
            if (score == 0) {
                continue;
            }
            double distance = distanceSqr(from, stand);
            if (score > bestScore || (score == bestScore && distance < bestDistance)) {
                best = stand;
                bestScore = score;
                bestDistance = distance;
            }
        }
        return best;
    }

    static int reachFor(int spiritHorizontal) {
        return Math.min(spiritHorizontal, (int) BlockBreaker.REACH);
    }

    static double distanceSqr(BlockCoord from, Stand stand) {
        double dx = from.x() - stand.x();
        double dy = from.y() - stand.y();
        double dz = from.z() - stand.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
