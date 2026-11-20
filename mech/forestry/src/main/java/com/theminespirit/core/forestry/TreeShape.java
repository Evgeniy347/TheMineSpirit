package com.theminespirit.core.forestry;

import com.theminespirit.core.forestry.api.ChopTuning;
import com.theminespirit.core.forestry.api.TreeView;
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
    private TreeShape() {}

    public static TreeDetection detect(BlockCoord start, TreeView view) {
        return detect(start, view, false);
    }

    public static TreeDetection detect(BlockCoord start, TreeView view, boolean allowNoSoil) {
        return detect(start, view, allowNoSoil, false);
    }

    public static TreeDetection detect(BlockCoord start, TreeView view, boolean allowNoSoil, boolean orchardCare) {
        if (!view.isLog(start)) {
            throw new IllegalArgumentException("Разбор дерева начинается с бревна, а " + start
                    + " бревном не является: поиск обязан подать координату из найденных брёвен");
        }
        Set<BlockCoord> logs = new LinkedHashSet<>();
        Set<BlockCoord> examined = new HashSet<>();
        Set<BlockCoord> leaves = new HashSet<>();
        boolean builtNearby = false;
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
                    logs.add(neighbour);
                    if (logs.size() > ChopTuning.MAX_LOGS) {
                        return new NotATree(NotATree.Reason.TOO_BIG, Set.copyOf(logs));
                    }
                    queue.add(neighbour);
                } else if (view.isNaturalLeaves(neighbour)) {
                    leaves.add(neighbour);
                } else if (view.isArtificial(neighbour)) {
                    builtNearby = true;
                }
            }
        }
        if (!hasAllowedExtent(logs)) {
            return new NotATree(NotATree.Reason.TOO_BIG, Set.copyOf(logs));
        }
        if (builtNearby && !orchardCare) {
            // lib: в саду ствол стоит вплотную к ограде, и соседний столб не делает дерево постройкой.
            return new NotATree(NotATree.Reason.BUILT_NEARBY, Set.copyOf(logs));
        }
        if (!hasTrunkBase(logs)) {
            return new NotATree(NotATree.Reason.NOT_A_TRUNK, Set.copyOf(logs));
        }
        BlockCoord base = base(logs);
        if (!allowNoSoil && !view.isSoil(new BlockCoord(base.x(), base.y() - 1, base.z()))) {
            return new NotATree(NotATree.Reason.NO_SOIL, Set.copyOf(logs));
        }
        if (leaves.size() < ChopTuning.MIN_NATURAL_LEAVES && orchardCare) {
            leaves.addAll(bandLeaves(logs, view));
            // lib: в саду ствол на месте саженца опознан местом, а не кроной: прошлая рубка могла снять
            // листву и оставить столб — такой ствол всё равно рубится, иначе сад зарастает обрубками.
        }
        if (leaves.size() < ChopTuning.MIN_NATURAL_LEAVES && !orchardCare) {
            return new NotATree(NotATree.Reason.NO_LEAVES, Set.copyOf(logs));
        }
        if (logs.size() < ChopTuning.MIN_TRUNK_LOGS) {
            return new NotATree(NotATree.Reason.NOT_A_TRUNK, Set.copyOf(logs));
        }
        return new Tree(base, List.copyOf(logs));
    }

    private static BlockCoord base(Set<BlockCoord> logs) {
        return logs.stream()
                .min(Comparator.comparingInt(BlockCoord::y)
                        .thenComparingInt(BlockCoord::x)
                        .thenComparingInt(BlockCoord::z))
                .orElseThrow();
    }

    private static boolean hasAllowedExtent(Set<BlockCoord> logs) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockCoord log : logs) {
            minX = Math.min(minX, log.x());
            maxX = Math.max(maxX, log.x());
            minZ = Math.min(minZ, log.z());
            maxZ = Math.max(maxZ, log.z());
        }
        return maxX - minX <= 2 * ChopTuning.MAX_SPREAD && maxZ - minZ <= 2 * ChopTuning.MAX_SPREAD;
    }

    private static boolean hasTrunkBase(Set<BlockCoord> logs) {
        int baseY = logs.stream().mapToInt(BlockCoord::y).min().orElseThrow();
        int count = 0;
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockCoord log : logs) {
            if (log.y() == baseY) {
                count++;
                minX = Math.min(minX, log.x());
                maxX = Math.max(maxX, log.x());
                minZ = Math.min(minZ, log.z());
                maxZ = Math.max(maxZ, log.z());
            }
        }
        return count <= ChopTuning.TRUNK_BASE_MAX_LOGS
                && maxX - minX <= ChopTuning.TRUNK_BASE_SPREAD
                && maxZ - minZ <= ChopTuning.TRUNK_BASE_SPREAD;
    }

    private static Set<BlockCoord> bandLeaves(Set<BlockCoord> logs, TreeView view) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockCoord log : logs) {
            minX = Math.min(minX, log.x());
            maxX = Math.max(maxX, log.x());
            minY = Math.min(minY, log.y());
            maxY = Math.max(maxY, log.y());
            minZ = Math.min(minZ, log.z());
            maxZ = Math.max(maxZ, log.z());
        }
        Set<BlockCoord> found = new HashSet<>();
        int reach = ChopTuning.ORCHARD_LEAF_REACH;
        for (int x = minX - reach; x <= maxX + reach; x++) {
            for (int y = minY - reach; y <= maxY + reach; y++) {
                for (int z = minZ - reach; z <= maxZ + reach; z++) {
                    BlockCoord cell = new BlockCoord(x, y, z);
                    if (!logs.contains(cell) && view.isNaturalLeaves(cell)) {
                        found.add(cell);
                    }
                }
            }
        }
        return found;
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
