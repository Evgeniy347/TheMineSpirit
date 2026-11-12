package com.theminespirit.core.town;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

public final class AccessPath {
    public static final int RADIUS = 48;
    private static final int MARGIN = 4;
    private static final int WALK = 1;
    private static final int DIG = 10;
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    public record Plan(List<BlockCoord> cut, List<BlockCoord> fill, List<BlockCoord> unreachable) {
        public Plan {
            cut = List.copyOf(cut);
            fill = List.copyOf(fill);
            unreachable = List.copyOf(unreachable);
        }

        public boolean clear() {
            return cut.isEmpty() && fill.isEmpty();
        }
    }

    private record Node(int x, int y, int z) {}

    private record Step(Node from, List<BlockCoord> cut, List<BlockCoord> fill) {}

    private AccessPath() {}

    public static Plan plan(
            SettlementScan.World world, Predicate<String> keep, BlockCoord start, List<BlockCoord> targets) {
        Objects.requireNonNull(world, "Мир не может быть null: проход ищется по его блокам");
        Objects.requireNonNull(keep, "Правило «не трогать» не может быть null: сундуки и станции не срезаются");
        Objects.requireNonNull(start, "Начало прохода не может быть null: проход ведётся от дома");
        Objects.requireNonNull(targets, "Цели прохода не могут быть null: нет целей — пустой список");
        int radius = 0;
        for (BlockCoord target : targets) {
            radius = Math.max(radius, Math.max(Math.abs(target.x() - start.x()), Math.abs(target.z() - start.z())));
        }
        radius = Math.min(RADIUS, radius + MARGIN);
        Set<BlockCoord> waiting = new LinkedHashSet<>(targets);
        Map<Node, Integer> cost = new HashMap<>();
        Map<Node, Step> steps = new HashMap<>();
        Node origin = new Node(start.x(), start.y(), start.z());
        cost.put(origin, 0);
        PriorityQueue<Object[]> queue = new PriorityQueue<>((a, b) -> Integer.compare((int) a[0], (int) b[0]));
        queue.add(new Object[] {0, origin});
        while (!queue.isEmpty() && !waiting.isEmpty()) {
            Object[] head = queue.poll();
            int here = (int) head[0];
            Node node = (Node) head[1];
            if (here > cost.getOrDefault(node, Integer.MAX_VALUE)) {
                continue;
            }
            waiting.removeIf(target -> beside(node, target));
            for (int[] side : SIDES) {
                int nx = node.x() + side[0];
                int nz = node.z() + side[1];
                if (Math.max(Math.abs(nx - start.x()), Math.abs(nz - start.z())) > radius || !world.loaded(nx, nz)) {
                    continue;
                }
                Step step = move(world, keep, node, nx, nz);
                if (step == null) {
                    continue;
                }
                Node next = step.from();
                int price = here + WALK + DIG * (step.cut().size() + step.fill().size());
                if (price < cost.getOrDefault(next, Integer.MAX_VALUE)) {
                    cost.put(next, price);
                    steps.put(next, new Step(node, step.cut(), step.fill()));
                    queue.add(new Object[] {price, next});
                }
            }
        }
        Set<BlockCoord> cut = new LinkedHashSet<>();
        Set<BlockCoord> fill = new LinkedHashSet<>();
        List<BlockCoord> unreachable = new ArrayList<>();
        for (BlockCoord target : targets) {
            Node best = null;
            int bestCost = Integer.MAX_VALUE;
            for (Map.Entry<Node, Integer> entry : cost.entrySet()) {
                Node node = entry.getKey();
                if (beside(node, target) && entry.getValue() < bestCost) {
                    best = node;
                    bestCost = entry.getValue();
                }
            }
            if (best == null) {
                unreachable.add(target);
                continue;
            }
            for (Node node = best;
                    steps.containsKey(node);
                    node = steps.get(node).from()) {
                cut.addAll(steps.get(node).cut());
                fill.addAll(steps.get(node).fill());
            }
        }
        return new Plan(List.copyOf(cut), List.copyOf(fill), unreachable);
    }

    private static @Nullable Step move(SettlementScan.World world, Predicate<String> keep, Node from, int x, int z) {
        int y = from.y();
        if (standable(world, x, y, z)) {
            return new Step(new Node(x, y, z), List.of(), List.of());
        }
        if (standable(world, x, y + 1, z) && world.open(from.x(), y + 2, from.z())) {
            return new Step(new Node(x, y + 1, z), List.of(), List.of());
        }
        if (standable(world, x, y - 1, z) && world.open(x, y + 1, z)) {
            return new Step(new Node(x, y - 1, z), List.of(), List.of());
        }
        List<BlockCoord> cut = new ArrayList<>();
        for (int dy = 0; dy <= 1; dy++) {
            if (world.open(x, y + dy, z)) {
                continue;
            }
            if (!diggable(world, keep, x, y + dy, z)) {
                return null;
            }
            cut.add(new BlockCoord(x, y + dy, z));
        }
        List<BlockCoord> fill = world.open(x, y - 1, z) ? List.of(new BlockCoord(x, y - 1, z)) : List.of();
        return new Step(new Node(x, y, z), cut, fill);
    }

    private static boolean beside(Node node, BlockCoord target) {
        return Math.abs(node.x() - target.x()) + Math.abs(node.z() - target.z()) == 1
                && Math.abs(node.y() - target.y()) <= 1;
    }

    private static boolean standable(SettlementScan.World world, int x, int y, int z) {
        return world.open(x, y, z) && world.open(x, y + 1, z) && !world.open(x, y - 1, z);
    }

    private static boolean diggable(SettlementScan.World world, Predicate<String> keep, int x, int y, int z) {
        return !world.planned(x, y, z)
                && !world.registered(x, y, z)
                && !world.playerPlaced(x, y, z)
                && !keep.test(world.blockId(x, y, z));
    }
}
