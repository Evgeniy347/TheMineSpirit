package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

public final class FillOrder {
    private FillOrder() {}

    public interface World {
        boolean solid(BlockCoord at);

        boolean fluid(BlockCoord at);
    }

    public record Box(int x1, int y1, int z1, int x2, int y2, int z2) implements FillArea {}

    public record Plan(List<BlockCoord> cells, List<BlockCoord> blocked) {
        public Plan {
            cells = List.copyOf(Objects.requireNonNull(cells, "Порядок клеток не может быть null"));
            blocked = List.copyOf(Objects.requireNonNull(blocked, "Заблокированные клетки не могут быть null"));
        }

        public boolean complete() {
            return blocked.isEmpty();
        }
    }

    public static Plan fill(List<BlockCoord> cells, Set<BlockCoord> occupied, World world) {
        return fill(cells, occupied, world, null);
    }

    public static Plan fill(
            List<BlockCoord> cells, Set<BlockCoord> occupied, World world, @Nullable BlockCoord anchor) {
        Objects.requireNonNull(cells, "Список ячеек не может быть null: без него неизвестно, что ставить");
        Objects.requireNonNull(occupied, "Занятые клетки не могут быть null: пустое задают пустым множеством");
        Objects.requireNonNull(world, "Мир не может быть null: без него неизвестна опора клетки");
        Map<Integer, List<BlockCoord>> layers = new TreeMap<>();
        Set<BlockCoord> unique = new LinkedHashSet<>(cells);
        Set<BlockCoord> fluid = new HashSet<>();
        for (BlockCoord cell : unique) {
            if (world.fluid(cell)) {
                fluid.add(cell);
            }
        }
        for (BlockCoord cell : unique) {
            layers.computeIfAbsent(cell.y(), ignored -> new ArrayList<>()).add(cell);
        }
        Comparator<BlockCoord> withinLayer = Comparator.comparing((BlockCoord cell) -> !fluid.contains(cell))
                .thenComparing(
                        anchor == null
                                ? Comparator.comparingInt(BlockCoord::x)
                                : Comparator.comparingLong((BlockCoord cell) -> -distanceSquared(anchor, cell)))
                .thenComparingInt(BlockCoord::x)
                .thenComparingInt(BlockCoord::z);
        List<BlockCoord> ordered = new ArrayList<>();
        List<BlockCoord> blocked = new ArrayList<>();
        Set<BlockCoord> placed = new HashSet<>();
        for (List<BlockCoord> layer : layers.values()) {
            layer.sort(withinLayer);
            boolean complete = true;
            for (BlockCoord cell : layer) {
                if (world.solid(cell)) {
                    continue;
                }
                if (occupied.contains(cell)) {
                    blocked.add(cell);
                    complete = false;
                    continue;
                }
                if (!supported(cell, world, placed)) {
                    blocked.add(cell);
                    complete = false;
                    continue;
                }
                ordered.add(cell);
                placed.add(cell);
            }
            if (!complete) {
                break;
            }
        }
        return new Plan(ordered, blocked);
    }

    public static boolean filled(Collection<BlockCoord> cells, World world) {
        Objects.requireNonNull(cells, "Клетки готовности не могут быть null: пустой регион задают пустым списком");
        Objects.requireNonNull(world, "Мир не может быть null: готовность читается по нему");
        for (BlockCoord cell : cells) {
            if (!world.solid(cell)) {
                return false;
            }
        }
        return true;
    }

    public static List<Box> layers(List<? extends FillArea> fills) {
        Objects.requireNonNull(fills, "Ящики подсыпки не могут быть null");
        List<Box> out = new ArrayList<>();
        int bottom = fills.stream().mapToInt(FillArea::y1).min().orElse(0);
        int top = fills.stream().mapToInt(FillArea::y2).max().orElse(-1);
        for (int level = bottom; level <= top; level++) {
            int y = level;
            List<Box> rows = fills.stream()
                    .filter(box -> box.y1() <= y && y <= box.y2())
                    .map(box -> new Box(box.x1(), y, box.z1(), box.x2(), y, box.z2()))
                    .toList();
            out.addAll(merged(rows));
        }
        return List.copyOf(out);
    }

    public static List<List<Box>> batches(List<? extends FillArea> layers, int batch) {
        Objects.requireNonNull(layers, "Слои подсыпки не могут быть null: нет подсыпки — пустой список");
        if (batch < 1) {
            throw new IllegalArgumentException("Партия земли " + batch + ": хотя бы блок");
        }
        List<List<Box>> out = new ArrayList<>();
        List<Box> open = new ArrayList<>();
        int volume = 0;
        for (FillArea layer : layers) {
            int layerVolume = layer.volume();
            if (!open.isEmpty() && volume + layerVolume > batch) {
                out.add(List.copyOf(open));
                open.clear();
                volume = 0;
            }
            open.add(new Box(layer.x1(), layer.y1(), layer.z1(), layer.x2(), layer.y2(), layer.z2()));
            volume += layerVolume;
        }
        if (!open.isEmpty()) {
            out.add(List.copyOf(open));
        }
        return List.copyOf(out);
    }

    public static int volume(Collection<? extends FillArea> boxes) {
        return boxes.stream().mapToInt(FillArea::volume).sum();
    }

    private static List<Box> merged(List<Box> rows) {
        List<Box> sorted = rows.stream()
                .sorted(Comparator.comparingInt(Box::x1)
                        .thenComparingInt(Box::x2)
                        .thenComparingInt(Box::z1))
                .toList();
        List<Box> out = new ArrayList<>();
        Box open = null;
        for (Box row : sorted) {
            if (open != null && open.x1() == row.x1() && open.x2() == row.x2() && open.z2() + 1 == row.z1()) {
                open = new Box(open.x1(), open.y1(), open.z1(), open.x2(), open.y2(), row.z2());
                continue;
            }
            if (open != null) {
                out.add(open);
            }
            open = row;
        }
        if (open != null) {
            out.add(open);
        }
        return List.copyOf(out);
    }

    private static boolean supported(BlockCoord cell, World world, Set<BlockCoord> placed) {
        BlockCoord below = new BlockCoord(cell.x(), cell.y() - 1, cell.z());
        return placed.contains(below) || world.solid(below);
    }

    private static long distanceSquared(BlockCoord from, BlockCoord to) {
        long dx = (long) to.x() - from.x();
        long dz = (long) to.z() - from.z();
        return dx * dx + dz * dz;
    }
}
