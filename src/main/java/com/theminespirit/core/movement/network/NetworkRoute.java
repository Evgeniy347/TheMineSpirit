package com.theminespirit.core.movement.network;

import com.theminespirit.core.building.BuildBox;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.movement.BodyStep;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;

public final class NetworkRoute {
    static final int ACCESS_STEPS = 24;
    static final int LEG_STEPS = 10;
    static final int ACCESS_NODES = 4096;
    static final int REPAIR_SLACK = 4;
    static final int REPAIR_NODES = 2048;
    private static final int STAND_RADIUS = 2;
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private NetworkRoute() {}

    public static Optional<List<BlockCoord>> plan(
            PathNetwork network, BlockCoord feet, BlockCoord target, SafetyView view) {
        Objects.requireNonNull(network, "Сеть не может быть null: по ней ищется маршрут");
        Objects.requireNonNull(feet, "Ноги духа не могут быть null: от них идёт маршрут");
        Objects.requireNonNull(target, "Цель не может быть null: к ней идёт маршрут");
        Objects.requireNonNull(view, "Мир не может быть null: по нему ищутся вход и выход сети");
        Optional<List<BlockCoord>> entry =
                standingNear(feet, view).flatMap(cell -> access(cell, view, network, Direction.FORWARD));
        Optional<List<BlockCoord>> exit =
                standingNear(target, view).flatMap(cell -> access(cell, view, network, Direction.BOTH));
        if (entry.isEmpty() || exit.isEmpty()) {
            return Optional.empty();
        }
        List<BlockCoord> in = entry.get();
        List<BlockCoord> out = new ArrayList<>(exit.get());
        Collections.reverse(out);
        Optional<List<BlockCoord>> over = network.route(in.get(in.size() - 1), out.get(0));
        if (over.isEmpty()) {
            return Optional.empty();
        }
        List<BlockCoord> points = new ArrayList<>(legPoints(in));
        points.remove(points.size() - 1);
        points.addAll(over.get());
        points.remove(points.size() - 1);
        points.addAll(legPoints(out));
        List<BlockCoord> distinct = new ArrayList<>();
        for (BlockCoord point : points) {
            if (distinct.isEmpty() || !distinct.get(distinct.size() - 1).equals(point)) {
                distinct.add(point);
            }
        }
        return Optional.of(List.copyOf(distinct));
    }

    private static List<BlockCoord> legPoints(List<BlockCoord> path) {
        List<BlockCoord> points = new ArrayList<>();
        for (int i = LEG_STEPS; i < path.size() - 1; i += LEG_STEPS) {
            points.add(path.get(i));
        }
        points.add(0, path.get(0));
        points.add(path.get(path.size() - 1));
        return points;
    }

    public static Optional<BlockCoord> standingNear(BlockCoord cell, SafetyView view) {
        for (int dy : new int[] {0, -1, 1}) {
            for (int radius = 0; radius <= STAND_RADIUS; radius++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                            continue;
                        }
                        BlockCoord candidate = new BlockCoord(cell.x() + dx, cell.y() + dy, cell.z() + dz);
                        if (MiningSafety.canStand(candidate, view)) {
                            return Optional.of(candidate);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private enum Direction {
        FORWARD,
        BOTH
    }

    private static Optional<List<BlockCoord>> access(
            BlockCoord start, SafetyView view, PathNetwork network, Direction direction) {
        Map<BlockCoord, BlockCoord> parent = new HashMap<>();
        Map<BlockCoord, Integer> depth = new HashMap<>();
        Queue<BlockCoord> queue = new ArrayDeque<>();
        depth.put(start, 0);
        queue.add(start);
        while (!queue.isEmpty() && depth.size() < ACCESS_NODES) {
            BlockCoord from = queue.poll();
            if (network.contains(from)) {
                List<BlockCoord> path = new ArrayList<>();
                for (BlockCoord cell = from; cell != null; cell = parent.get(cell)) {
                    path.add(cell);
                }
                Collections.reverse(path);
                return Optional.of(List.copyOf(path));
            }
            int steps = depth.getOrDefault(from, ACCESS_STEPS);
            if (steps >= ACCESS_STEPS) {
                continue;
            }
            for (int[] side : SIDES) {
                BlockCoord next = direction == Direction.FORWARD
                        ? BodyStep.step(from, side[0], side[1], view)
                        : BodyStep.twoWay(from, side[0], side[1], view);
                if (next != null && depth.putIfAbsent(next, steps + 1) == null) {
                    parent.put(next, from);
                    queue.add(next);
                }
            }
        }
        return Optional.empty();
    }

    public static Optional<List<BlockCoord>> repair(PathSegment segment, SafetyView view) {
        Objects.requireNonNull(segment, "Отрезок не может быть null: его концы — начало и конец пересчёта");
        Objects.requireNonNull(view, "Мир не может быть null: по нему пересчитывается отрезок");
        BlockCoord from = segment.first();
        BlockCoord to = segment.last();
        if (!MiningSafety.canStand(from, view) || !MiningSafety.canStand(to, view)) {
            return Optional.empty();
        }
        BuildBox bounds = around(segment.cells(), REPAIR_SLACK);
        Map<BlockCoord, BlockCoord> parent = new HashMap<>();
        Queue<BlockCoord> queue = new ArrayDeque<>();
        parent.put(from, from);
        queue.add(from);
        while (!queue.isEmpty() && parent.size() < REPAIR_NODES) {
            BlockCoord cell = queue.poll();
            if (cell.equals(to)) {
                List<BlockCoord> path = new ArrayList<>();
                for (BlockCoord c = to;
                        !c.equals(from);
                        c = Objects.requireNonNull(parent.get(c), "обход потерял шаг")) {
                    path.add(c);
                }
                path.add(from);
                Collections.reverse(path);
                return PathSegment.lengthOf(path, 0, path.size() - 1) <= PathSegment.MAX_LENGTH
                        ? Optional.of(List.copyOf(path))
                        : Optional.empty();
            }
            for (int[] side : SIDES) {
                BlockCoord next = BodyStep.twoWay(cell, side[0], side[1], view);
                if (next != null && bounds.contains(next) && parent.putIfAbsent(next, cell) == null) {
                    queue.add(next);
                }
            }
        }
        return Optional.empty();
    }

    private static BuildBox around(List<BlockCoord> cells, int slack) {
        BuildBox box = BuildBox.of(
                cells.get(0).x(),
                cells.get(0).y(),
                cells.get(0).z(),
                cells.get(0).x(),
                cells.get(0).y(),
                cells.get(0).z());
        for (BlockCoord cell : cells) {
            box = BuildBox.of(
                    Math.min(box.minX(), cell.x()),
                    Math.min(box.minY(), cell.y()),
                    Math.min(box.minZ(), cell.z()),
                    Math.max(box.maxX(), cell.x()),
                    Math.max(box.maxY(), cell.y()),
                    Math.max(box.maxZ(), cell.z()));
        }
        return BuildBox.of(
                box.minX() - slack,
                box.minY() - slack,
                box.minZ() - slack,
                box.maxX() + slack,
                box.maxY() + slack,
                box.maxZ() + slack);
    }
}
