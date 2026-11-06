package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

public final class BuildOrder {
    private BuildOrder() {}

    public interface World {
        boolean solid(BlockCoord at);

        boolean fluid(BlockCoord at);
    }

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
        for (BlockCoord cell : new LinkedHashSet<>(cells)) {
            layers.computeIfAbsent(cell.y(), ignored -> new ArrayList<>()).add(cell);
        }
        Comparator<BlockCoord> withinLayer = Comparator.comparing((BlockCoord cell) -> !world.fluid(cell))
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
