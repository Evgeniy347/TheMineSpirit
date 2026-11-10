package com.theminespirit.neoforge.board;

import com.theminespirit.core.forestry.TreeSight;
import com.theminespirit.core.forestry.TreeSpecies;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.LevelTreeView;
import com.theminespirit.neoforge.TreeBlocks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;

final class TreeSurroundings {
    private static final String TREE_PREFIX = "tree:";
    private static final int TARGET_LIMIT = 4;
    private static final int TARGET_SEPARATION = 8;

    record Found(
            Map<String, BlockCoord> nearest, Map<String, Integer> distances, Map<String, List<BlockCoord>> targets) {}

    private TreeSurroundings() {}

    static Found scan(ServerLevel level, Map<String, List<BlockCoord>> logs, BlockCoord home) {
        if (logs.isEmpty()) {
            return new Found(Map.of(), Map.of(), Map.of());
        }
        Map<String, List<TreeSight.Stand>> found = new HashMap<>();
        for (TreeSight.Stand stand : TreeSight.detect(
                logs, species -> new LevelTreeView(level, TreeBlocks.logs(TreeSpecies.parse(species))))) {
            found.computeIfAbsent(stand.species(), key -> new ArrayList<>()).add(stand);
        }
        Map<String, BlockCoord> nearest = new HashMap<>();
        Map<String, Integer> distances = new HashMap<>();
        Map<String, List<BlockCoord>> targets = new HashMap<>();
        found.forEach((species, stands) -> {
            List<BlockCoord> bases = stands.stream()
                    .map(TreeSight.Stand::base)
                    .sorted(Comparator.comparingInt(base -> distanceSquared(base, home)))
                    .toList();
            List<BlockCoord> chosen = separated(bases);
            if (!chosen.isEmpty()) {
                String tag = TREE_PREFIX + species;
                nearest.put(tag, chosen.get(0));
                distances.put(tag, (int) Math.round(Math.sqrt(distanceSquared(chosen.get(0), home))));
                targets.put(tag, chosen);
            }
        });
        return new Found(Map.copyOf(nearest), Map.copyOf(distances), Map.copyOf(targets));
    }

    private static List<BlockCoord> separated(List<BlockCoord> bases) {
        List<BlockCoord> chosen = new ArrayList<>();
        for (BlockCoord base : bases) {
            if (chosen.size() >= TARGET_LIMIT) {
                break;
            }
            boolean distinct =
                    chosen.stream().allMatch(at -> distanceSquared(at, base) >= TARGET_SEPARATION * TARGET_SEPARATION);
            if (distinct) {
                chosen.add(base);
            }
        }
        return List.copyOf(chosen);
    }

    private static int distanceSquared(BlockCoord one, BlockCoord two) {
        int dx = one.x() - two.x();
        int dy = one.y() - two.y();
        int dz = one.z() - two.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
