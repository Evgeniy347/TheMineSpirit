package com.theminespirit.core.forestry;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public final class TreeCanopy {
    static final int MARGIN = 6;

    private TreeCanopy() {}

    public static List<BlockCoord> plan(Tree tree, TreeView view) {
        Objects.requireNonNull(tree, "Дерево не может быть null: без него неизвестно, чью крону снимать");
        Objects.requireNonNull(view, "TreeView не может быть null: по нему отличают листву от прочих блоков");
        Set<BlockCoord> logs = new HashSet<>(tree.logs());
        Bounds box = Bounds.of(tree);
        List<BlockCoord> foreign = foreignLogs(box, view, logs);
        Set<BlockCoord> seen = new HashSet<>(logs);
        Deque<BlockCoord> queue = new ArrayDeque<>();
        for (BlockCoord log : tree.logs()) {
            forEachNeighbor(log, cell -> {
                if (box.contains(cell)
                        && seen.add(cell)
                        && view.isNaturalLeaves(cell)
                        && ownedBy(cell, tree.logs(), foreign)) {
                    queue.add(cell);
                }
            });
        }
        List<BlockCoord> found = new ArrayList<>(queue);
        while (!queue.isEmpty()) {
            forEachNeighbor(queue.poll(), cell -> {
                if (!box.contains(cell) || !seen.add(cell)) {
                    return;
                }
                if (view.isAnyLog(cell) && !logs.contains(cell)) {
                    return;
                }
                if (view.isNaturalLeaves(cell) && ownedBy(cell, tree.logs(), foreign)) {
                    queue.add(cell);
                    found.add(cell);
                }
            });
        }
        found.sort(Comparator.comparingInt(BlockCoord::y)
                .reversed()
                .thenComparingLong(cell -> distanceSquared(cell, tree.base()))
                .thenComparingInt(BlockCoord::x)
                .thenComparingInt(BlockCoord::z));
        return List.copyOf(found);
    }

    private static List<BlockCoord> foreignLogs(Bounds box, TreeView view, Set<BlockCoord> logs) {
        List<BlockCoord> foreign = new ArrayList<>();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    BlockCoord cell = new BlockCoord(x, y, z);
                    if (!logs.contains(cell) && view.isAnyLog(cell)) {
                        foreign.add(cell);
                    }
                }
            }
        }
        return foreign;
    }

    private static boolean ownedBy(BlockCoord cell, List<BlockCoord> logs, List<BlockCoord> foreign) {
        long self = nearestSquared(cell, logs);
        long other = nearestSquared(cell, foreign);
        return self < other;
    }

    private static long nearestSquared(BlockCoord cell, List<BlockCoord> cells) {
        long best = Long.MAX_VALUE;
        for (BlockCoord other : cells) {
            best = Math.min(best, distanceSquared(cell, other));
        }
        return best;
    }

    private static void forEachNeighbor(BlockCoord cell, Consumer<BlockCoord> action) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        action.accept(new BlockCoord(cell.x() + dx, cell.y() + dy, cell.z() + dz));
                    }
                }
            }
        }
    }

    private static long distanceSquared(BlockCoord first, BlockCoord second) {
        long dx = (long) first.x() - second.x();
        long dy = (long) first.y() - second.y();
        long dz = (long) first.z() - second.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        static Bounds of(Tree tree) {
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (BlockCoord log : tree.logs()) {
                minX = Math.min(minX, log.x());
                minY = Math.min(minY, log.y());
                minZ = Math.min(minZ, log.z());
                maxX = Math.max(maxX, log.x());
                maxY = Math.max(maxY, log.y());
                maxZ = Math.max(maxZ, log.z());
            }
            return new Bounds(minX - MARGIN, minY, minZ - MARGIN, maxX + MARGIN, maxY + MARGIN, maxZ + MARGIN);
        }

        boolean contains(BlockCoord cell) {
            return cell.x() >= minX
                    && cell.x() <= maxX
                    && cell.y() >= minY
                    && cell.y() <= maxY
                    && cell.z() >= minZ
                    && cell.z() <= maxZ;
        }
    }
}
