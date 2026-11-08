package com.theminespirit.core.town;

import com.theminespirit.core.building.FillArea;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class PitGroups {
    public static final int GAP = 2;

    private PitGroups() {}

    public record Pit(
            String key, BlockCoord anchor, List<BlockCoord> columns, List<BlockCoord> cells, int blocks, int depth) {
        public Pit {
            Objects.requireNonNull(key, "Ключ ямы не может быть null: по нему доска находит задание");
            Objects.requireNonNull(anchor, "Якорь ямы не может быть null: к нему идёт дух");
            columns = List.copyOf(Objects.requireNonNull(columns, "Столбцы ямы не могут быть null"));
            cells = List.copyOf(Objects.requireNonNull(cells, "Клетки ямы не могут быть null"));
            if (columns.isEmpty() || blocks < 1 || depth < 1) {
                throw new IllegalArgumentException("Яма без столбцов, блоков или глубины: " + columns.size()
                        + " столбцов, " + blocks + " блоков, глубина " + depth);
            }
        }
    }

    private record Column(BlockCoord top, int depth) {}

    public static List<Pit> group(Map<BlockCoord, Integer> pits) {
        Objects.requireNonNull(pits, "Карта ям не может быть null: пустую задают пустой картой");
        List<Column> columns = pits.entrySet().stream()
                .map(entry -> new Column(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt((Column column) -> column.top().x())
                        .thenComparingInt(column -> column.top().z())
                        .thenComparingInt(column -> column.top().y()))
                .toList();
        int[] parent = new int[columns.size()];
        for (int index = 0; index < parent.length; index++) {
            parent[index] = index;
        }
        for (int first = 0; first < columns.size(); first++) {
            for (int second = first + 1; second < columns.size(); second++) {
                if (near(columns.get(first).top(), columns.get(second).top())) {
                    union(parent, first, second);
                }
            }
        }
        Map<Integer, List<Column>> grouped = new LinkedHashMap<>();
        for (int index = 0; index < columns.size(); index++) {
            grouped.computeIfAbsent(root(parent, index), ignored -> new ArrayList<>())
                    .add(columns.get(index));
        }
        List<Pit> pitsOut = new ArrayList<>();
        for (List<Column> group : grouped.values()) {
            pitsOut.add(pit(group));
        }
        pitsOut.sort(Comparator.comparing(Pit::anchor));
        return List.copyOf(pitsOut);
    }

    public static boolean overlaps(Collection<BlockCoord> cells, FillArea area) {
        for (BlockCoord cell : cells) {
            if (cell.x() >= area.x1()
                    && cell.x() <= area.x2()
                    && cell.y() >= area.y1()
                    && cell.y() <= area.y2()
                    && cell.z() >= area.z1()
                    && cell.z() <= area.z2()) {
                return true;
            }
        }
        return false;
    }

    private static Pit pit(List<Column> group) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int depth = 0;
        List<BlockCoord> columns = new ArrayList<>();
        List<BlockCoord> cells = new ArrayList<>();
        for (Column column : group) {
            BlockCoord top = column.top();
            minX = Math.min(minX, top.x());
            minZ = Math.min(minZ, top.z());
            maxX = Math.max(maxX, top.x());
            maxZ = Math.max(maxZ, top.z());
            depth = Math.max(depth, column.depth());
            columns.add(top);
            for (int y = top.y() - column.depth() + 1; y <= top.y(); y++) {
                cells.add(new BlockCoord(top.x(), y, top.z()));
            }
        }
        columns.sort(Comparator.comparingInt(BlockCoord::x).thenComparingInt(BlockCoord::z));
        cells.sort(Comparator.comparingInt(BlockCoord::y)
                .thenComparingInt(BlockCoord::x)
                .thenComparingInt(BlockCoord::z));
        String key = minX + "," + minZ + ":" + (maxX - minX + 1) + "x" + (maxZ - minZ + 1);
        return new Pit(key, columns.get(0), columns, cells, cells.size(), depth);
    }

    private static boolean near(BlockCoord first, BlockCoord second) {
        int dx = Math.abs(first.x() - second.x());
        int dz = Math.abs(first.z() - second.z());
        return Math.max(dx, dz) <= GAP + 1;
    }

    private static int root(int[] parent, int index) {
        int root = index;
        while (parent[root] != root) {
            root = parent[root];
        }
        int at = index;
        while (parent[at] != at) {
            int next = parent[at];
            parent[at] = root;
            at = next;
        }
        return root;
    }

    private static void union(int[] parent, int first, int second) {
        int left = root(parent, first);
        int right = root(parent, second);
        if (left != right) {
            parent[right] = left;
        }
    }
}
