package com.theminespirit.neoforge;

import com.theminespirit.core.forestry.FellOrder;
import com.theminespirit.core.forestry.TreeView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class TreeFelling {
    private final TreeView view;
    private final Set<BlockCoord> known = new HashSet<>();

    TreeFelling(TreeView view) {
        this.view = view;
    }

    void extend(List<BlockCoord> cells) {
        List<BlockCoord> fell = new ArrayList<>();
        Set<BlockCoord> added = new HashSet<>();
        for (BlockCoord cell : List.copyOf(cells)) {
            if (!view.isLog(cell) || !known.add(cell)) {
                continue;
            }
            List<BlockCoord> tree = FellOrder.of(cell, view);
            known.addAll(tree);
            for (BlockCoord log : tree) {
                if (added.add(log)) {
                    fell.add(log);
                }
            }
        }
        if (fell.isEmpty()) {
            return;
        }
        Set<BlockCoord> order = new HashSet<>(fell);
        List<BlockCoord> rest =
                cells.stream().filter(cell -> !order.contains(cell)).toList();
        cells.clear();
        cells.addAll(fell);
        cells.addAll(rest);
    }
}
