package com.theminespirit.core.forestry;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record ChopPlan(List<BlockCoord> order) {
    public ChopPlan {
        order = List.copyOf(Objects.requireNonNull(order, "order не может быть null: пустой список — законный результат"));
    }

    public static ChopPlan of(Tree tree, BlockCoord from) {
        Objects.requireNonNull(tree, "tree не может быть null: план рубки строится по разобранному дереву");
        Objects.requireNonNull(from, "from не может быть null: порядок рубки считается от стоянки духа");
        List<BlockCoord> ordered = tree.logs().stream().sorted(farthestFirst(from)).toList();
        return new ChopPlan(ordered);
    }

    private static Comparator<BlockCoord> farthestFirst(BlockCoord from) {
        return Comparator.comparingLong((BlockCoord coord) -> distanceSquared(coord, from)).reversed()
                .thenComparing(Comparator.comparingInt(BlockCoord::y).reversed())
                .thenComparingInt(BlockCoord::x)
                .thenComparingInt(BlockCoord::z);
    }

    private static long distanceSquared(BlockCoord first, BlockCoord second) {
        long dx = (long) first.x() - second.x();
        long dy = (long) first.y() - second.y();
        long dz = (long) first.z() - second.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
