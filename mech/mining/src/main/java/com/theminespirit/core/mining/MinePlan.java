package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

public final class MinePlan {
    private MinePlan() {}

    public static List<MineColumn> columns(Collection<BlockCoord> floors, Collection<BlockCoord> air) {
        Objects.requireNonNull(floors, "Пол не может быть null: подземная схема без пола не задаёт, где стоять");
        Objects.requireNonNull(air, "Воздух не может быть null: проход без воздуха — пустой список");
        Map<Long, NavigableMap<Integer, BlockCoord>> floorsByColumn = new HashMap<>();
        for (BlockCoord floor : floors) {
            floorsByColumn
                    .computeIfAbsent(key(floor.x(), floor.z()), ignored -> new TreeMap<>())
                    .put(floor.y(), floor);
        }
        Map<BlockCoord, Integer> heights = new HashMap<>();
        for (BlockCoord cell : air) {
            NavigableMap<Integer, BlockCoord> below = floorsByColumn.get(key(cell.x(), cell.z()));
            Map.Entry<Integer, BlockCoord> floor = below == null ? null : below.lowerEntry(cell.y());
            if (floor == null) {
                throw new IllegalArgumentException("Клетка воздуха " + cell + " без пола под ней: дух не встанет "
                        + "в проходе; под каждой воздушной колонкой подземной схемы обязан быть @floor");
            }
            heights.merge(floor.getValue(), cell.y() - floor.getKey(), Math::max);
        }
        List<MineColumn> columns = new ArrayList<>();
        for (Map.Entry<BlockCoord, Integer> entry : heights.entrySet()) {
            columns.add(new MineColumn(entry.getKey(), entry.getValue()));
        }
        columns.sort(Comparator.comparing(MineColumn::floor));
        return columns;
    }

    public static List<MineColumn> order(List<MineColumn> columns, BlockCoord entrance) {
        Objects.requireNonNull(columns, "Колонки не могут быть null: пустая схема — пустой список");
        Objects.requireNonNull(entrance, "Вход не может быть null: без него неизвестно, откуда копать");
        if (columns.isEmpty()) {
            return List.of();
        }
        MineColumn start = columns.stream()
                .filter(column -> column.contains(entrance) || column.floor().equals(entrance))
                .findFirst()
                .orElseGet(() -> nearest(columns, entrance));
        Set<MineColumn> seen = new LinkedHashSet<>();
        Deque<MineColumn> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty()) {
            MineColumn current = queue.poll();
            for (MineColumn other : columns) {
                if (!seen.contains(other) && current.walksTo(other)) {
                    seen.add(other);
                    queue.add(other);
                }
            }
        }
        List<MineColumn> ordered = new ArrayList<>(seen);
        List<MineColumn> rest = new ArrayList<>(columns);
        rest.removeAll(seen);
        rest.sort(Comparator.comparingLong(column -> distance(column.floor(), entrance)));
        ordered.addAll(rest);
        return List.copyOf(ordered);
    }

    public static Optional<MineColumn> next(List<MineColumn> order, Set<MineColumn> open, Set<MineColumn> closed) {
        Objects.requireNonNull(order, "Порядок не может быть null");
        Objects.requireNonNull(open, "Открытые колонки не могут быть null: в начале — пустое множество");
        Objects.requireNonNull(closed, "Закрытые колонки не могут быть null: без руды в проходе — пустое множество");
        for (MineColumn column : order) {
            if (open.contains(column) || closed.contains(column)) {
                continue;
            }
            if (open.isEmpty() || parent(column, open).isPresent()) {
                return Optional.of(column);
            }
        }
        return Optional.empty();
    }

    public static Optional<MineColumn> parent(MineColumn column, Set<MineColumn> open) {
        return open.stream()
                .filter(column::walksTo)
                .min(Comparator.comparingInt((MineColumn other) ->
                                Math.abs(other.floor().y() - column.floor().y()))
                        .thenComparing(MineColumn::floor));
    }

    public static Optional<MineColumn> forward(MineColumn parent, MineColumn blocked, List<MineColumn> plan) {
        int dx = blocked.floor().x() - parent.floor().x();
        int dz = blocked.floor().z() - parent.floor().z();
        return plan.stream()
                .filter(column -> column.floor().x() == blocked.floor().x() + dx
                        && column.floor().z() == blocked.floor().z() + dz
                        && blocked.walksTo(column))
                .findFirst();
    }

    private static MineColumn nearest(List<MineColumn> columns, BlockCoord entrance) {
        return columns.stream()
                .min(Comparator.comparingLong((MineColumn column) -> distance(column.floor(), entrance))
                        .thenComparing(MineColumn::floor))
                .orElseThrow();
    }

    private static long distance(BlockCoord a, BlockCoord b) {
        long dx = (long) a.x() - b.x();
        long dy = (long) a.y() - b.y();
        long dz = (long) a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
