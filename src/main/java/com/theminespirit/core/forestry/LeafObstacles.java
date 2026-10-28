package com.theminespirit.core.forestry;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class LeafObstacles {
    private static final int STAND_HEIGHT = 2;
    private static final int[][] SIDES = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};

    private LeafObstacles() {}

    public static List<BlockCoord> blockingStand(Tree tree, TreeView view, BlockCoord from) {
        Objects.requireNonNull(tree, "Дерево не может быть null: без него неизвестно, у какого ствола чистить стоянку");
        Objects.requireNonNull(view, "TreeView не может быть null: по нему отличают листву от прочих блоков");
        Objects.requireNonNull(from, "Точка отсчёта не может быть null: от неё считается порядок ломки листвы");
        int baseY = tree.base().y();
        Set<BlockCoord> cells = new LinkedHashSet<>();
        for (BlockCoord log : tree.logs()) {
            if (log.y() != baseY) {
                continue;
            }
            for (int[] side : SIDES) {
                for (int dy = 0; dy < STAND_HEIGHT; dy++) {
                    cells.add(new BlockCoord(log.x() + side[0], baseY + dy, log.z() + side[1]));
                }
            }
        }
        List<BlockCoord> leaves = new ArrayList<>();
        for (BlockCoord cell : cells) {
            if (view.isNaturalLeaves(cell)) {
                leaves.add(cell);
            }
        }
        leaves.sort(Comparator.comparingLong((BlockCoord cell) -> distanceSquared(cell, from))
                .thenComparingInt(BlockCoord::y)
                .thenComparingInt(BlockCoord::x)
                .thenComparingInt(BlockCoord::z));
        return List.copyOf(leaves);
    }

    private static long distanceSquared(BlockCoord first, BlockCoord second) {
        long dx = (long) first.x() - second.x();
        long dy = (long) first.y() - second.y();
        long dz = (long) first.z() - second.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
