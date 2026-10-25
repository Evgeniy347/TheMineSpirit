package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

final class SchematicWideApproach {
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int REQUIRED_WIDTH = 2;

    private SchematicWideApproach() {}

    static void check(
            SchematicRules.Structure structure,
            Map<BlockCoord, String> grid,
            SchematicWalk.Result walk,
            List<SchematicRules.Violation> violations) {
        if (structure.type() != BuildingType.HOUSING) {
            return;
        }
        List<StairPair> stairs = wideBottoms(structure, grid);
        if (stairs.isEmpty()) {
            return;
        }
        Optional<List<BlockCoord>> doors = DoorPairs.pairAt(structure.schematic(), structure.entry());
        if (doors.isEmpty()) {
            violations.add(new SchematicRules.Violation(
                    SchematicRules.RuleId.STAIR_APPROACH,
                    "лестница шириной 2 идёт от входа, а вход не двойной: нужен проём в две створки, иначе к широкой "
                            + "лестнице не подойти вдвоём (владелец 20.10)"));
            return;
        }
        for (StairPair pair : stairs) {
            if (widePath(walk.feet(), grid, structure.schematic(), doors.get(), pair)) {
                continue;
            }
            BlockCoord first = pair.first();
            BlockCoord second = pair.second();
            violations.add(new SchematicRules.Violation(
                    SchematicRules.RuleId.STAIR_APPROACH,
                    "к лестнице шириной 2 [" + first.x() + "," + first.y() + "," + first.z() + "]–[" + second.x() + ","
                            + second.y() + "," + second.z() + "] нет дорожки шириной 2 от двойного входа или площадка "
                            + "перед ней уже двух клеток: на пути сужение до одной клетки или прыжок — освободите "
                            + "подход на две клетки вперёд (владелец 20.10)"));
            return;
        }
    }

    private record Bottom(BlockCoord approach, BlockCoord riser, Facing facing, Set<BlockCoord> run) {}

    private record StairPair(BlockCoord first, BlockCoord second, Facing away, Set<BlockCoord> forbidden) {}

    private static List<StairPair> wideBottoms(SchematicRules.Structure structure, Map<BlockCoord, String> grid) {
        Map<BlockCoord, Schematic.Entry> stairs = new HashMap<>();
        for (Schematic.Entry entry : structure.schematic().blocks()) {
            if (SchematicBlocks.path(entry.block()).endsWith("_stairs")) {
                stairs.put(new BlockCoord(entry.dx(), entry.dy(), entry.dz()), entry);
            }
        }
        List<Bottom> bottoms = new ArrayList<>();
        for (Map.Entry<BlockCoord, Schematic.Entry> stair : stairs.entrySet()) {
            BlockCoord riser = stair.getKey();
            Optional<Facing> facing = Facing.of(stair.getValue().state().getOrDefault("facing", "north"));
            if (facing.isEmpty()) {
                continue;
            }
            Facing up = facing.get();
            Set<BlockCoord> run = run(stairs, riser, up);
            if (run.size() < REQUIRED_WIDTH) {
                continue;
            }
            BlockCoord approach = new BlockCoord(
                    riser.x() + up.opposite().dx(),
                    riser.y(),
                    riser.z() + up.opposite().dz());
            if (!inside(approach, structure.schematic())
                    || !SchematicWalk.standableAt(
                            grid,
                            structure.schematic(),
                            new BlockCoord(approach.x(), approach.y() - 1, approach.z()))) {
                continue;
            }
            String below = grid.get(new BlockCoord(approach.x(), approach.y() - 1, approach.z()));
            if (below != null) {
                String path = SchematicBlocks.path(below);
                if (path.endsWith("_stairs") || path.endsWith("_slab")) {
                    continue;
                }
            }
            bottoms.add(new Bottom(approach, riser, up, run));
        }
        List<StairPair> pairs = new ArrayList<>();
        for (Bottom first : bottoms) {
            for (int[] side : SIDES) {
                BlockCoord secondApproach = new BlockCoord(
                        first.approach().x() + side[0],
                        first.approach().y(),
                        first.approach().z() + side[1]);
                for (Bottom second : bottoms) {
                    if (!second.approach().equals(secondApproach) || second.facing() != first.facing()) {
                        continue;
                    }
                    if (second.riser().y() != first.riser().y()) {
                        continue;
                    }
                    if (Math.abs(second.riser().x() - first.riser().x())
                                    + Math.abs(
                                            second.riser().z() - first.riser().z())
                            != 1) {
                        continue;
                    }
                    if (first.approach().compareTo(second.approach()) >= 0) {
                        continue;
                    }
                    Set<BlockCoord> forbidden = new HashSet<>(first.run());
                    forbidden.addAll(second.run());
                    pairs.add(new StairPair(
                            first.approach(), second.approach(), first.facing().opposite(), forbidden));
                }
            }
        }
        return pairs;
    }

    private static Set<BlockCoord> run(Map<BlockCoord, Schematic.Entry> stairs, BlockCoord riser, Facing up) {
        Set<BlockCoord> run = new LinkedHashSet<>();
        BlockCoord current = riser;
        while (stairs.containsKey(current)) {
            run.add(new BlockCoord(current.x(), current.y() + 1, current.z()));
            current = new BlockCoord(current.x() + up.dx(), current.y() + 1, current.z() + up.dz());
        }
        return run;
    }

    private static boolean widePath(
            Set<BlockCoord> feet,
            Map<BlockCoord, String> grid,
            Schematic schematic,
            List<BlockCoord> doors,
            StairPair target) {
        if (!feet.contains(target.first()) || !feet.contains(target.second())) {
            return false;
        }
        BlockCoord behindFirst = new BlockCoord(
                target.first().x() + target.away().dx(),
                target.first().y(),
                target.first().z() + target.away().dz());
        BlockCoord behindSecond = new BlockCoord(
                target.second().x() + target.away().dx(),
                target.second().y(),
                target.second().z() + target.away().dz());
        if (!feet.contains(behindFirst) || !feet.contains(behindSecond)) {
            return false;
        }
        if (doors.size() != REQUIRED_WIDTH || !feet.contains(doors.get(0)) || !feet.contains(doors.get(1))) {
            return false;
        }
        BlockCoord firstDoor = doors.get(0);
        BlockCoord secondDoor = doors.get(1);
        if (firstDoor.y() != secondDoor.y()
                || Math.abs(firstDoor.x() - secondDoor.x()) + Math.abs(firstDoor.z() - secondDoor.z()) != 1) {
            return false;
        }
        Map<BlockCoord, List<BlockCoord>> moves = new HashMap<>();
        for (BlockCoord cell : feet) {
            moves.put(cell, List.copyOf(SchematicWalk.steps(grid, schematic, cell)));
        }
        PairKey start = PairKey.of(firstDoor, secondDoor);
        PairKey goal = PairKey.of(target.first(), target.second());
        Set<PairKey> seen = new HashSet<>();
        Deque<PairKey> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            PairKey pair = queue.poll();
            if (pair.equals(goal)) {
                return true;
            }
            for (int[] side : SIDES) {
                BlockCoord nextFirst = move(moves.get(pair.first()), pair.first(), side[0], side[1]);
                BlockCoord nextSecond = move(moves.get(pair.second()), pair.second(), side[0], side[1]);
                if (nextFirst == null || nextSecond == null || nextFirst.y() != nextSecond.y()) {
                    continue;
                }
                if (Math.abs(nextFirst.x() - nextSecond.x()) + Math.abs(nextFirst.z() - nextSecond.z()) != 1) {
                    continue;
                }
                if (target.forbidden().contains(nextFirst) || target.forbidden().contains(nextSecond)) {
                    continue;
                }
                PairKey next = PairKey.of(nextFirst, nextSecond);
                if (seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return false;
    }

    private static boolean inside(BlockCoord cell, Schematic schematic) {
        return cell.x() >= 0
                && cell.y() >= 0
                && cell.z() >= 0
                && cell.x() < schematic.sizeX()
                && cell.y() < schematic.sizeY()
                && cell.z() < schematic.sizeZ();
    }

    private static @Nullable BlockCoord move(@Nullable List<BlockCoord> next, BlockCoord from, int dx, int dz) {
        if (next == null) {
            return null;
        }
        for (BlockCoord cell : next) {
            if (cell.x() == from.x() + dx && cell.z() == from.z() + dz) {
                return cell;
            }
        }
        return null;
    }

    private record PairKey(BlockCoord first, BlockCoord second) {
        static PairKey of(BlockCoord first, BlockCoord second) {
            return first.compareTo(second) <= 0 ? new PairKey(first, second) : new PairKey(second, first);
        }
    }
}
