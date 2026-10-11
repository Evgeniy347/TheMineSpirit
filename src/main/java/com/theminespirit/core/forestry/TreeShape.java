package com.theminespirit.core.forestry;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class TreeShape {
    private TreeShape() {
    }

    public static TreeDetection detect(BlockCoord start, TreeView view) {
        if (!view.isLog(start)) {
            throw new IllegalArgumentException("Разбор дерева начинается с бревна, а " + start
                    + " бревном не является: поиск обязан подать координату из найденных брёвен");
        }
        Set<BlockCoord> logs = new LinkedHashSet<>();
        Set<BlockCoord> examined = new HashSet<>();
        Set<BlockCoord> leaves = new HashSet<>();
        Deque<BlockCoord> queue = new ArrayDeque<>();
        logs.add(start);
        examined.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockCoord current = queue.poll();
            for (BlockCoord neighbour : neighbours(current)) {
                if (!examined.add(neighbour)) {
                    continue;
                }
                if (view.isLog(neighbour)) {
                    if (spread(neighbour, start) > ChopTuning.MAX_SPREAD) {
                        return new NotATree(NotATree.Reason.TOO_BIG);
                    }
                    logs.add(neighbour);
                    if (logs.size() > ChopTuning.MAX_LOGS) {
                        return new NotATree(NotATree.Reason.TOO_BIG);
                    }
                    queue.add(neighbour);
                } else if (view.isNaturalLeaves(neighbour)) {
                    leaves.add(neighbour);
                }
            }
        }
        BlockCoord base = base(logs);
        if (!view.isSoil(new BlockCoord(base.x(), base.y() - 1, base.z()))) {
            return new NotATree(NotATree.Reason.NO_SOIL);
        }
        if (leaves.size() < ChopTuning.MIN_NATURAL_LEAVES) {
            return new NotATree(NotATree.Reason.NO_LEAVES);
        }
        return new Tree(base, List.copyOf(logs));
    }

    private static BlockCoord base(Set<BlockCoord> logs) {
        return logs.stream().min(Comparator.comparingInt(BlockCoord::y)
                .thenComparingInt(BlockCoord::x)
                .thenComparingInt(BlockCoord::z)).orElseThrow();
    }

    private static int spread(BlockCoord coord, BlockCoord start) {
        return Math.max(Math.abs(coord.x() - start.x()), Math.abs(coord.z() - start.z()));
    }

    private static List<BlockCoord> neighbours(BlockCoord coord) {
        List<BlockCoord> result = new ArrayList<>(26);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        result.add(new BlockCoord(coord.x() + dx, coord.y() + dy, coord.z() + dz));
                    }
                }
            }
        }
        return result;
    }
}
