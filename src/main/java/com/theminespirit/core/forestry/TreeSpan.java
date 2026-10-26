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
import java.util.function.Predicate;

public final class TreeSpan {
    public static final int MAX_LOGS = 64;

    private TreeSpan() {}

    public static List<BlockCoord> of(BlockCoord start, Predicate<BlockCoord> log) {
        Objects.requireNonNull(start, "Клетка ствола не может быть null: от неё ищется всё дерево");
        Objects.requireNonNull(log, "Признак бревна не может быть null: без него дерево не отличить от постройки");
        Set<BlockCoord> seen = new HashSet<>();
        Deque<BlockCoord> queue = new ArrayDeque<>();
        if (log.test(start)) {
            seen.add(start);
            queue.add(start);
        }
        while (!queue.isEmpty() && seen.size() < MAX_LOGS) {
            BlockCoord cell = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockCoord near = new BlockCoord(cell.x() + dx, cell.y() + dy, cell.z() + dz);
                        if (seen.size() < MAX_LOGS && !seen.contains(near) && log.test(near)) {
                            seen.add(near);
                            queue.add(near);
                        }
                    }
                }
            }
        }
        List<BlockCoord> tree = new ArrayList<>(seen);
        tree.sort(Comparator.comparingInt(BlockCoord::y).reversed().thenComparing(Comparator.naturalOrder()));
        return List.copyOf(tree);
    }
}
