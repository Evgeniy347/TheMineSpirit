package com.theminespirit.core.movement.network;

import com.theminespirit.core.building.BuildBox;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.movement.BodyStep;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class NetworkBuilder {
    public static final int MAX_NODES = 32_768;
    static final int MIN_FLOOR_CELLS = 4;
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private NetworkBuilder() {}

    public static List<List<BlockCoord>> chains(List<BlockCoord> anchors, BuildBox box, SafetyView view) {
        Objects.requireNonNull(anchors, "Начала сети не могут быть null: пустой список задаётся List.of()");
        Objects.requireNonNull(box, "Габарит не может быть null: без него обход мира не кончится");
        Objects.requireNonNull(view, "Мир не может быть null: по нему ищутся клетки, где пройти");
        Map<BlockCoord, BlockCoord> parent = new HashMap<>();
        Set<BlockCoord> seen = new HashSet<>();
        Set<BlockCoord> roots = new LinkedHashSet<>();
        Set<BlockCoord> targets = new LinkedHashSet<>();
        for (BlockCoord anchor : anchors) {
            if (seen.contains(anchor)) {
                targets.add(anchor);
            } else if (box.contains(anchor) && MiningSafety.canStand(anchor, view)) {
                roots.add(anchor);
                targets.addAll(floorEnds(walk(anchor, box, view, seen, parent)));
            }
        }
        Set<BlockCoord> tree = new LinkedHashSet<>();
        for (BlockCoord target : targets) {
            for (BlockCoord cell = target; cell != null && tree.add(cell); cell = parent.get(cell)) {}
        }
        Map<BlockCoord, Integer> children = new HashMap<>();
        for (BlockCoord cell : tree) {
            BlockCoord up = parent.get(cell);
            if (up != null) {
                children.merge(up, 1, Integer::sum);
            }
        }
        Set<BlockCoord> nodes = new TreeSet<>(roots);
        nodes.addAll(targets);
        for (BlockCoord cell : tree) {
            if (children.getOrDefault(cell, 0) != 1) {
                nodes.add(cell);
            }
        }
        List<List<BlockCoord>> chains = new ArrayList<>();
        for (BlockCoord node : nodes) {
            BlockCoord up = parent.get(node);
            if (up == null) {
                continue;
            }
            List<BlockCoord> chain = new ArrayList<>();
            chain.add(node);
            BlockCoord cell = up;
            chain.add(cell);
            while (!nodes.contains(cell)) {
                cell = Objects.requireNonNull(parent.get(cell), "ветвь дерева оборвалась до начала сети");
                chain.add(cell);
            }
            chains.addAll(cut(chain));
        }
        return List.copyOf(chains);
    }

    static List<List<BlockCoord>> cut(List<BlockCoord> chain) {
        List<List<BlockCoord>> pieces = new ArrayList<>();
        int start = 0;
        double length = 0;
        for (int i = 1; i < chain.size(); i++) {
            double step = BodyStep.cost(chain.get(i - 1), chain.get(i));
            if (length + step > PathSegment.MAX_LENGTH) {
                pieces.add(List.copyOf(chain.subList(start, i)));
                start = i - 1;
                length = 0;
            }
            length += step;
        }
        pieces.add(List.copyOf(chain.subList(start, chain.size())));
        return pieces;
    }

    private static List<BlockCoord> walk(
            BlockCoord root, BuildBox box, SafetyView view, Set<BlockCoord> seen, Map<BlockCoord, BlockCoord> parent) {
        List<BlockCoord> order = new ArrayList<>();
        Queue<BlockCoord> queue = new ArrayDeque<>();
        seen.add(root);
        queue.add(root);
        while (!queue.isEmpty()) {
            BlockCoord from = queue.poll();
            order.add(from);
            for (int[] side : SIDES) {
                if (seen.size() >= MAX_NODES) {
                    return order;
                }
                BlockCoord next = BodyStep.twoWay(from, side[0], side[1], view);
                if (next != null && box.contains(next) && seen.add(next)) {
                    parent.put(next, from);
                    queue.add(next);
                }
            }
        }
        return order;
    }

    private static List<BlockCoord> floorEnds(List<BlockCoord> order) {
        Map<Integer, BlockCoord> last = new TreeMap<>();
        Map<Integer, Integer> count = new HashMap<>();
        for (BlockCoord cell : order) {
            last.put(cell.y(), cell);
            count.merge(cell.y(), 1, Integer::sum);
        }
        List<BlockCoord> ends = new ArrayList<>();
        for (Map.Entry<Integer, BlockCoord> floor : last.entrySet()) {
            if (count.getOrDefault(floor.getKey(), 0) >= MIN_FLOOR_CELLS) {
                ends.add(floor.getValue());
            }
        }
        return ends;
    }
}
