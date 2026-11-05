package com.theminespirit.core.forestry;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record ChopPlan(List<BlockCoord> order) {
    public ChopPlan {
        order = List.copyOf(
                Objects.requireNonNull(order, "order не может быть null: пустой список — законный результат"));
    }

    public static ChopPlan of(Tree tree, BlockCoord from) {
        Objects.requireNonNull(tree, "tree не может быть null: план рубки строится по разобранному дереву");
        Objects.requireNonNull(from, "from не может быть null: порядок рубки считается от стоянки духа");
        List<BlockCoord> ordered = tree.logs().stream().sorted(topFirst(from)).toList();
        return new ChopPlan(ordered);
    }

    public ChopPlan rest(TreeView view, BlockCoord from) {
        Objects.requireNonNull(view, "view не может быть null: остаток ствола считается по миру после прохода");
        Objects.requireNonNull(from, "from не может быть null: порядок добивки считается от второй стоянки");
        return new ChopPlan(
                order.stream().filter(view::isLog).sorted(topFirst(from)).toList());
    }

    private static Comparator<BlockCoord> topFirst(BlockCoord from) {
        return Comparator.comparingInt(BlockCoord::y)
                .reversed()
                .thenComparing(Comparator.comparingLong((BlockCoord coord) -> distanceSquared(coord, from))
                        .reversed())
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
