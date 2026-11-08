package com.theminespirit.core.forestry;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public record ChopPlan(List<BlockCoord> order) {
    public ChopPlan {
        order = List.copyOf(
                Objects.requireNonNull(order, "order не может быть null: пустой список — законный результат"));
    }

    public static ChopPlan of(Tree tree, BlockCoord from) {
        return of(tree, from, cell -> false);
    }

    public static ChopPlan of(Tree tree, BlockCoord from, Predicate<BlockCoord> protectedCell) {
        Objects.requireNonNull(tree, "tree не может быть null: план рубки строится по разобранному дереву");
        Objects.requireNonNull(from, "from не может быть null: порядок рубки считается от стоянки духа");
        Objects.requireNonNull(
                protectedCell, "Признак защищённой клетки не может быть null: без него рубка снимает брёвна постройки");
        List<BlockCoord> ordered = tree.logs().stream()
                .filter(cell -> !protectedCell.test(cell))
                .sorted(topFirst(from))
                .toList();
        return new ChopPlan(ordered);
    }

    public ChopPlan rest(TreeView view, BlockCoord from) {
        return rest(view, from, cell -> false);
    }

    public ChopPlan rest(TreeView view, BlockCoord from, Predicate<BlockCoord> protectedCell) {
        Objects.requireNonNull(view, "view не может быть null: остаток ствола считается по миру после прохода");
        Objects.requireNonNull(from, "from не может быть null: порядок добивки считается от второй стоянки");
        Objects.requireNonNull(
                protectedCell,
                "Признак защищённой клетки не может быть null: без него добивка снова возьмёт брёвна постройки");
        return new ChopPlan(order.stream()
                .filter(view::isLog)
                .filter(cell -> !protectedCell.test(cell))
                .sorted(topFirst(from))
                .toList());
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
