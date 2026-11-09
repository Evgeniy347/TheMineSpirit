package com.theminespirit.core.movement;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import org.jspecify.annotations.Nullable;

final class PitStairPlanner {
    private static final int DIG_COST = 10;
    private static final int HARD_COST = 2;
    private static final int PLACE_COST = 30;

    private PitStairPlanner() {}

    static PitExitPlanner.@Nullable Plan stairs(
            BlockCoord feet,
            PitExitPlanner.World world,
            PitExitPlanner.Supplies supplies,
            Set<BlockCoord> protectedCells,
            int surface) {
        Search search = search(feet, world, supplies, protectedCells, surface);
        if (search.goal() == null) {
            return null;
        }
        return reconstruct(feet, world, supplies, search);
    }

    private record Search(@Nullable BlockCoord goal, Map<BlockCoord, Step> came) {}

    private static Search search(
            BlockCoord feet,
            PitExitPlanner.World world,
            PitExitPlanner.Supplies supplies,
            Set<BlockCoord> protectedCells,
            int surface) {
        Queue<Node> queue = new PriorityQueue<>(Comparator.comparingInt(Node::cost)
                .thenComparingInt(node -> node.at().y()));
        Map<BlockCoord, Step> came = new HashMap<>();
        Map<BlockCoord, Integer> best = new HashMap<>();
        queue.add(new Node(feet, 0, Set.of(), Set.of()));
        best.put(feet, 0);
        Context context = new Context(feet, world, supplies, protectedCells, came, best, queue);
        while (!queue.isEmpty()) {
            Node node = queue.poll();
            if (node.cost() > best.getOrDefault(node.at(), Integer.MAX_VALUE)) {
                continue;
            }
            if (node.at().y() > feet.y()
                    && reachedSurface(node.at(), feet, world, node.dug(), node.placed(), surface)) {
                return new Search(node.at(), came);
            }
            explore(node, context);
        }
        return new Search(null, came);
    }

    private record Context(
            BlockCoord feet,
            PitExitPlanner.World world,
            PitExitPlanner.Supplies supplies,
            Set<BlockCoord> protectedCells,
            Map<BlockCoord, Step> came,
            Map<BlockCoord, Integer> best,
            Queue<Node> queue) {}

    private static void explore(Node node, Context context) {
        BlockCoord feet = context.feet();
        PitExitPlanner.World world = context.world();
        Set<BlockCoord> protectedCells = context.protectedCells();
        Map<BlockCoord, Step> came = context.came();
        Map<BlockCoord, Integer> best = context.best();
        Queue<Node> queue = context.queue();
        for (int[] side : sides()) {
            BlockCoord flat = new BlockCoord(
                    node.at().x() + side[0], node.at().y(), node.at().z() + side[1]);
            if (within(feet, flat)
                    && !protectedCells.contains(flat)
                    && standableAfter(flat, world, node.dug(), node.placed())
                    && best.getOrDefault(flat, Integer.MAX_VALUE) > node.cost() + 1) {
                Step walk = new Step(node.at(), List.of(), List.of(), 1);
                best.put(flat, node.cost() + 1);
                came.put(flat, walk);
                queue.add(new Node(flat, node.cost() + 1, node.dug(), node.placed()));
            }
            BlockCoord next = new BlockCoord(
                    node.at().x() + side[0], node.at().y() + 1, node.at().z() + side[1]);
            if (!within(feet, next) || next.y() > feet.y() + PitExitPlanner.MAX_RISE) {
                continue;
            }
            Step step = step(node.at(), next, world, context.supplies(), protectedCells, node.dug(), node.placed());
            if (step == null) {
                continue;
            }
            int cost = node.cost() + step.cost();
            if (cost >= best.getOrDefault(next, Integer.MAX_VALUE)) {
                continue;
            }
            Set<BlockCoord> dug = union(node.dug(), step.dig());
            Set<BlockCoord> placed = union(node.placed(), step.place());
            best.put(next, cost);
            came.put(next, step);
            queue.add(new Node(next, cost, dug, placed));
        }
    }

    private static PitExitPlanner.@Nullable Plan reconstruct(
            BlockCoord feet, PitExitPlanner.World world, PitExitPlanner.Supplies supplies, Search search) {
        BlockCoord goal = Objects.requireNonNull(search.goal(), "Цель лестницы без клетки выхода");
        List<BlockCoord> dig = new ArrayList<>();
        List<BlockCoord> place = new ArrayList<>();
        Set<BlockCoord> seenDig = new LinkedHashSet<>();
        Set<BlockCoord> seenPlace = new LinkedHashSet<>();
        BlockCoord cursor = goal;
        Step step = search.came().get(cursor);
        while (step != null) {
            for (BlockCoord cell : step.dig()) {
                if (seenDig.add(cell)) {
                    dig.add(cell);
                }
            }
            for (BlockCoord cell : step.place()) {
                if (seenPlace.add(cell)) {
                    place.add(cell);
                }
            }
            cursor = step.from();
            step = search.came().get(cursor);
        }
        Collections.reverse(dig);
        Collections.reverse(place);
        int soft = 0;
        for (BlockCoord cell : dig) {
            if (world.at(cell) == PitExitPlanner.Kind.SOFT || world.at(cell) == PitExitPlanner.Kind.FALLING) {
                soft++;
            }
        }
        if (place.size() > supplies.blocks() + soft) {
            return null;
        }
        boolean hardOnly = !dig.isEmpty()
                && !supplies.pickaxe()
                && dig.stream().allMatch(cell -> world.at(cell) == PitExitPlanner.Kind.HARD);
        return new PitExitPlanner.Plan(
                PitExitPlanner.Method.STAIRS,
                goal,
                goal.y() - feet.y(),
                dig,
                place,
                hardOnly
                        ? "лестница в камне: без кирки режет аварийно, без добычи"
                        : "лестница-ступени в стене: " + dig.size() + " клеток, подъём " + (goal.y() - feet.y()));
    }

    static int surface(BlockCoord feet, PitExitPlanner.World world) {
        List<Integer> heights = new ArrayList<>();
        for (int dx = -PitExitPlanner.SCAN_RADIUS; dx <= PitExitPlanner.SCAN_RADIUS; dx++) {
            for (int dz = -PitExitPlanner.SCAN_RADIUS; dz <= PitExitPlanner.SCAN_RADIUS; dz++) {
                heights.add(
                        groundY(feet.x() + dx, feet.z() + dz, feet.y(), world).orElse(feet.y()));
            }
        }
        Collections.sort(heights);
        int index = Math.min(heights.size() - 1, (int) Math.round((heights.size() - 1) * 0.9));
        return heights.get(index);
    }

    static int groundYOrFeet(int x, int z, int from, PitExitPlanner.World world) {
        return groundY(x, z, from, world).orElse(from);
    }

    static OptionalInt groundY(int x, int z, int from, PitExitPlanner.World world) {
        for (int y = from; y <= from + PitExitPlanner.MAX_RISE; y++) {
            BlockCoord cell = new BlockCoord(x, y, z);
            if (PitExitPlanner.passable(world.at(cell))
                    && PitExitPlanner.passable(world.at(above(cell)))
                    && PitExitPlanner.supported(world.at(below(cell)))) {
                return OptionalInt.of(y);
            }
        }
        return OptionalInt.empty();
    }

    private static boolean reachedSurface(
            BlockCoord cell,
            BlockCoord feet,
            PitExitPlanner.World world,
            Set<BlockCoord> dug,
            Set<BlockCoord> placed,
            int surface) {
        if (!standableAfter(cell, world, dug, placed) || cell.y() < surface - 1) {
            return false;
        }
        OptionalInt ground = groundY(cell.x(), cell.z(), feet.y(), world);
        return ground.isPresent() && cell.y() == ground.getAsInt();
    }

    private static boolean standableAfter(
            BlockCoord cell, PitExitPlanner.World world, Set<BlockCoord> dug, Set<BlockCoord> placed) {
        return (PitExitPlanner.passable(world.at(cell)) || dug.contains(cell))
                && (PitExitPlanner.passable(world.at(above(cell))) || dug.contains(above(cell)))
                && supportAfter(below(cell), world, dug, placed);
    }

    private static boolean supportAfter(
            BlockCoord cell, PitExitPlanner.World world, Set<BlockCoord> dug, Set<BlockCoord> placed) {
        if (placed.contains(cell)) {
            return true;
        }
        return !dug.contains(cell) && PitExitPlanner.supported(world.at(cell));
    }

    private static Set<BlockCoord> union(Set<BlockCoord> first, List<BlockCoord> second) {
        if (second.isEmpty()) {
            return first;
        }
        Set<BlockCoord> all = new LinkedHashSet<>(first);
        all.addAll(second);
        return Set.copyOf(all);
    }

    private record Node(BlockCoord at, int cost, Set<BlockCoord> dug, Set<BlockCoord> placed) {}

    private record Step(BlockCoord from, List<BlockCoord> dig, List<BlockCoord> place, int cost) {}

    private static @Nullable Step step(
            BlockCoord from,
            BlockCoord next,
            PitExitPlanner.World world,
            PitExitPlanner.Supplies supplies,
            Set<BlockCoord> protectedCells,
            Set<BlockCoord> dugBefore,
            Set<BlockCoord> placedBefore) {
        List<BlockCoord> dig = new ArrayList<>();
        List<BlockCoord> place = new ArrayList<>();
        int cost = DIG_COST;
        BlockCoord over = above(above(from));
        if (!PitExitPlanner.passable(world.at(over))) {
            PitExitPlanner.Kind kind = world.at(over);
            if (!PitExitPlanner.breakable(kind)) {
                return null;
            }
            dig.add(over);
            cost += kind == PitExitPlanner.Kind.HARD ? HARD_COST : 0;
        }
        BlockCoord head = above(next);
        for (BlockCoord cell : List.of(next, head)) {
            if (PitExitPlanner.passable(world.at(cell))) {
                continue;
            }
            PitExitPlanner.Kind kind = world.at(cell);
            if (!PitExitPlanner.breakable(kind)) {
                return null;
            }
            dig.add(cell);
            cost += kind == PitExitPlanner.Kind.HARD ? HARD_COST : 0;
        }
        BlockCoord floor = below(next);
        PitExitPlanner.Kind floorKind = world.at(floor);
        if (floorKind == PitExitPlanner.Kind.LAVA || floorKind == PitExitPlanner.Kind.BEDROCK) {
            return null;
        }
        boolean floorSolid =
                placedBefore.contains(floor) || (!dugBefore.contains(floor) && PitExitPlanner.supported(floorKind));
        if (!floorSolid) {
            BlockCoord belowFloor = below(floor);
            boolean base = placedBefore.contains(belowFloor)
                    || (!dugBefore.contains(belowFloor) && PitExitPlanner.supported(world.at(belowFloor)));
            if (!base) {
                return null;
            }
            place.add(floor);
            cost += PLACE_COST;
        }
        for (BlockCoord cell : dig) {
            if (protectedCells.contains(cell) || PitExitPlanner.touchesLava(cell, world)) {
                return null;
            }
            if (world.at(below(cell)) == PitExitPlanner.Kind.FALLING && !dig.contains(below(cell))) {
                return null;
            }
        }
        for (BlockCoord cell : place) {
            if (protectedCells.contains(cell)) {
                return null;
            }
        }
        if (place.size() > supplies.blocks()) {
            cost += PLACE_COST;
        }
        return new Step(from, List.copyOf(dig), List.copyOf(place), cost);
    }

    private static boolean within(BlockCoord feet, BlockCoord cell) {
        return Math.abs(cell.x() - feet.x()) <= PitExitPlanner.RADIUS
                && Math.abs(cell.z() - feet.z()) <= PitExitPlanner.RADIUS;
    }

    private static BlockCoord above(BlockCoord cell) {
        return new BlockCoord(cell.x(), cell.y() + 1, cell.z());
    }

    private static BlockCoord below(BlockCoord cell) {
        return new BlockCoord(cell.x(), cell.y() - 1, cell.z());
    }

    private static int[][] sides() {
        return new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    }
}
