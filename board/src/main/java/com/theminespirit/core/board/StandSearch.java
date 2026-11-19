package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;
import java.util.function.Predicate;

public final class StandSearch {

    private StandSearch() {}

    public static BlockCoord nearest(BlockCoord center, Predicate<BlockCoord> stand) {
        Objects.requireNonNull(center, "Центр поиска не может быть null: от него ищется стоянка");
        Objects.requireNonNull(stand, "Признак стоянки не может быть null: без него клетку не выбрать");
        BlockCoord best = center;
        int bestCost = Integer.MAX_VALUE;
        for (int dx = -BoardTuning.standSearchRadius(); dx <= BoardTuning.standSearchRadius(); dx++) {
            for (int dz = -BoardTuning.standSearchRadius(); dz <= BoardTuning.standSearchRadius(); dz++) {
                for (int dy = -BoardTuning.standSearchHeight(); dy <= BoardTuning.standSearchHeight(); dy++) {
                    int cost = dx * dx + dz * dz + dy * dy;
                    BlockCoord cell = new BlockCoord(center.x() + dx, center.y() + dy, center.z() + dz);
                    if (cost < bestCost && stand.test(cell)) {
                        best = cell;
                        bestCost = cost;
                    }
                }
            }
        }
        return best;
    }
}
