package com.theminespirit.neoforge;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class ScaffoldTrail {
    private final Set<BlockCoord> cells = new LinkedHashSet<>();

    void add(Collection<BlockCoord> column) {
        cells.addAll(column);
    }

    boolean empty() {
        return cells.isEmpty();
    }

    List<BlockCoord> cells() {
        return List.copyOf(cells);
    }

    List<BlockCoord> topDown() {
        List<BlockCoord> order = new ArrayList<>(cells);
        order.sort(Comparator.comparingInt(BlockCoord::y).reversed());
        return List.copyOf(order);
    }
}
