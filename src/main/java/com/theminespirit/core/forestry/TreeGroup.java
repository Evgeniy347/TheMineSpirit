package com.theminespirit.core.forestry;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record TreeGroup(Set<BlockCoord> logs, Set<BlockCoord> leaves, boolean grounded, BlockCoord base) {
    private static final int MAX_LEAVES = 512;

    public TreeGroup {
        Objects.requireNonNull(logs, "Брёвна дерева не могут быть null: без них нечего рубить");
        Objects.requireNonNull(leaves, "Листва дерева не может быть null: дерево рубится вместе с ней");
        Objects.requireNonNull(base, "Низ дерева не может быть null: от него считается подход и опора");
        if (logs.isEmpty()) {
            throw new IllegalArgumentException("Дерево без брёвен: группа обязана содержать хотя бы нижнее бревно");
        }
    }

    public static Optional<TreeGroup> of(BlockCoord start, TreeView view) {
        Objects.requireNonNull(start, "Клетка начала не может быть null: от неё разбирается дерево");
        Objects.requireNonNull(view, "Вид мира не может быть null: без него дерево не собрать");
        Set<BlockCoord> logs = new LinkedHashSet<>();
        Set<BlockCoord> leaves = new LinkedHashSet<>();
        if (view.isLog(start)) {
            logs.add(start);
        } else if (!view.isNaturalLeaves(start)) {
            return Optional.empty();
        }
        Set<BlockCoord> seen = new LinkedHashSet<>();
        Deque<BlockCoord> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockCoord current = queue.poll();
            for (BlockCoord neighbour : neighbours(current)) {
                if (!seen.add(neighbour)) {
                    continue;
                }
                if (view.isLog(neighbour)) {
                    if (logs.size() >= ChopTuning.MAX_LOGS) {
                        return Optional.empty();
                    }
                    logs.add(neighbour);
                    queue.add(neighbour);
                } else if (view.isNaturalLeaves(neighbour)) {
                    if (leaves.size() >= MAX_LEAVES) {
                        return Optional.empty();
                    }
                    leaves.add(neighbour);
                    queue.add(neighbour);
                }
            }
        }
        if (logs.isEmpty()) {
            return Optional.empty();
        }
        BlockCoord base = logs.stream()
                .min(Comparator.comparingInt(BlockCoord::y)
                        .thenComparingInt(BlockCoord::x)
                        .thenComparingInt(BlockCoord::z))
                .orElseThrow();
        boolean grounded = logs.stream().anyMatch(cell -> {
            BlockCoord under = below(cell);
            return !logs.contains(under) && !leaves.contains(under) && !view.isEmpty(under);
        });
        return Optional.of(new TreeGroup(Set.copyOf(logs), Set.copyOf(leaves), grounded, base));
    }

    public Set<BlockCoord> cells() {
        Set<BlockCoord> all = new LinkedHashSet<>(logs);
        all.addAll(leaves);
        return Set.copyOf(all);
    }

    private static BlockCoord below(BlockCoord coord) {
        return new BlockCoord(coord.x(), coord.y() - 1, coord.z());
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
