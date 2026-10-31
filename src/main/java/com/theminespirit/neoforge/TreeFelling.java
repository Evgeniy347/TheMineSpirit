package com.theminespirit.neoforge;

import com.theminespirit.core.forestry.TreeGroup;
import com.theminespirit.core.forestry.TreeView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class TreeFelling {
    private final TreeView view;
    private final Map<BlockCoord, TreeGroup> known = new HashMap<>();
    private final Set<BlockCoord> removed = new HashSet<>();

    TreeFelling(TreeView view) {
        this.view = view;
    }

    void extend(List<BlockCoord> cells) {
        Set<BlockCoord> fell = new LinkedHashSet<>();
        for (BlockCoord cell : List.copyOf(cells)) {
            TreeGroup group = group(cell);
            if (group != null) {
                fell.addAll(group.cells());
            }
        }
        if (fell.isEmpty()) {
            return;
        }
        cells.addAll(0, fell.stream().filter(cell -> !cells.contains(cell)).toList());
    }

    TreeGroup group(BlockCoord cell) {
        if (!treeCell(cell)) {
            return null;
        }
        TreeGroup cached = known.get(cell);
        if (cached != null) {
            return cached;
        }
        TreeGroup group = TreeGroup.of(cell, view).orElse(null);
        if (group != null) {
            for (BlockCoord member : group.cells()) {
                known.put(member, group);
            }
        }
        return group;
    }

    boolean treeCell(BlockCoord cell) {
        return view.isLog(cell) || view.isNaturalLeaves(cell);
    }

    void removed(BlockCoord cell) {
        removed.add(cell);
    }

    int dropPartial(List<BlockCoord> remaining) {
        Set<BlockCoord> present = new HashSet<>(remaining);
        Set<TreeGroup> groups = new LinkedHashSet<>();
        for (BlockCoord cell : List.copyOf(remaining)) {
            TreeGroup group = group(cell);
            if (group != null) {
                groups.add(group);
            }
        }
        int dropped = 0;
        for (TreeGroup group : groups) {
            boolean whole = group.cells().stream().allMatch(cell -> present.contains(cell) || removed.contains(cell));
            if (whole) {
                continue;
            }
            for (BlockCoord cell : group.cells()) {
                if (present.remove(cell)) {
                    remaining.remove(cell);
                    dropped++;
                }
            }
        }
        return dropped;
    }
}
