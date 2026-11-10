package com.theminespirit.core.movement;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.route.RouteWorld;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

final class NavFuzzChecks {
    private static final int MAX_VISITS = 20_000;

    private NavFuzzChecks() {}

    static void checkSurfaceEscape(long seed, FuzzWorld world, List<String> failures) {
        Optional<List<BlockCoord>> found = SurfaceEscape.path(world.start(), world, world::sky);
        if (found.isPresent()) {
            List<BlockCoord> path = found.orElseThrow();
            if (!world.sky(path.get(path.size() - 1))) {
                failures.add(seed + " SurfaceEscape: путь кончается не под небом — " + path.get(path.size() - 1));
            }
            for (int index = 1; index < path.size(); index++) {
                BlockCoord from = path.get(index - 1);
                BlockCoord to = path.get(index);
                if (!BodyMotion.canMove(world, from, to)) {
                    failures.add(seed + " SurfaceEscape: шаг " + from + " → " + to + " неисполним телом");
                }
            }
        }
        if (!found.isPresent() && bodyWalkReachesSky(world, world.start())) {
            failures.add(seed + " SurfaceEscape: BodyMotion находит пеший выход к небу, а планировщик — нет");
        }
    }

    static void checkPitExit(long seed, FuzzWorld world, List<String> failures) {
        PitExitPlanner.Plan plan = PitExitPlanner.plan(
                world.start(), FuzzWorldViews.pitWorld(world), new PitExitPlanner.Supplies(8, true), Set.of());
        for (BlockCoord cell : plan.dig()) {
            if (world.protectedAt(cell)) {
                failures.add(seed + " PitExitPlanner: план ломает защищённый блок " + cell);
            }
        }
        for (BlockCoord cell : plan.place()) {
            if (world.protectedAt(cell)) {
                failures.add(seed + " PitExitPlanner: план ставит блок в защищённую клетку " + cell);
            }
        }
        if (!plan.possible()) {
            if (world.expectPitEscape() && !world.physicallyImpossible()) {
                failures.add(seed + " PitExitPlanner: с киркой и блоками выхода нет, хотя он физически возможен");
            }
            return;
        }
        switch (plan.method()) {
            case WALK -> {
                if (!walkReaches(world, world.start(), plan.exit(), BodyMotion.MAX_FALL)) {
                    failures.add(seed + " PitExitPlanner WALK: выход " + plan.exit() + " недостижим телом");
                }
            }
            case PILLAR -> {
                if (!pillarExecutable(world, world.start(), plan.rise(), plan.exit())) {
                    failures.add(seed + " PitExitPlanner PILLAR: столб на " + plan.rise() + " неисполним телом");
                }
            }
            case STAIRS -> {
                Overlay overlay = new Overlay(world);
                for (BlockCoord cell : plan.dig()) {
                    if (world.cellAt(cell) == FuzzWorld.Cell.CHEST
                            || world.cellAt(cell) == FuzzWorld.Cell.DOOR
                            || world.cellAt(cell) == FuzzWorld.Cell.TRAPDOOR
                            || world.cellAt(cell) == FuzzWorld.Cell.BED) {
                        failures.add(seed + " PitExitPlanner STAIRS: ломает защищённый блок " + cell);
                    }
                    overlay.set(cell, FuzzWorld.Cell.AIR);
                }
                for (BlockCoord cell : plan.place()) {
                    overlay.set(cell, FuzzWorld.Cell.DIRT);
                }
                if (!walkReaches(overlay, world.start(), plan.exit(), BodyMotion.MAX_FALL)) {
                    failures.add(seed + " PitExitPlanner STAIRS: после ломки и постановки выход " + plan.exit()
                            + " недостижим телом; dig=" + plan.dig() + " place=" + plan.place());
                }
            }
            case IMPOSSIBLE -> {}
        }
    }

    static void checkSelfEscape(long seed, FuzzWorld world, List<String> failures) {
        BlockCoord goal = world.surfaceTarget().orElse(BodyMotion.above(world.start()));
        Optional<SelfEscape.Plan> plan = SelfEscape.plan(
                world.start(), goal, FuzzWorldViews.grid(world, world.start()), true, world.stepBlocks());
        if (plan.isEmpty()) {
            return;
        }
        SelfEscape.Plan escape = plan.orElseThrow();
        switch (escape.method()) {
            case STEP -> {
                if (!pillarExecutable(world, world.start(), escape.rise(), escape.target())) {
                    failures.add(seed + " SelfEscape STEP: столб на " + escape.rise() + " неисполним телом");
                }
            }
            case DIG -> {
                Overlay overlay = new Overlay(world);
                for (BlockCoord cell : escape.blocks()) {
                    if (world.protectedAt(cell)) {
                        failures.add(seed + " SelfEscape DIG: ломает защищённый блок " + cell);
                    }
                    overlay.set(cell, FuzzWorld.Cell.AIR);
                }
                if (escape.rise() == 0 && !BodyMotion.canMove(overlay, world.start(), escape.target())) {
                    failures.add(seed + " SelfEscape DIG: проход " + world.start() + " → " + escape.target()
                            + " после ломки неисполним телом");
                }
                if (!BodyMotion.fits(overlay, escape.target())) {
                    failures.add(seed + " SelfEscape DIG: цель " + escape.target() + " не помещает тело");
                }
            }
            case DESCEND, GOTO -> {
                if (!BodyMotion.fits(world, escape.target())) {
                    failures.add(seed + " SelfEscape " + escape.method() + ": цель " + escape.target()
                            + " не помещает тело");
                }
            }
        }
    }

    private static boolean pillarExecutable(FuzzWorld world, BlockCoord feet, int rise, BlockCoord exit) {
        Overlay overlay = new Overlay(world);
        BlockCoord at = feet;
        if (!BodyMotion.fits(overlay, at)) {
            return false;
        }
        for (int step = 0; step < rise; step++) {
            overlay.set(at, FuzzWorld.Cell.DIRT);
            at = BodyMotion.above(at);
            if (!BodyMotion.fits(overlay, at)) {
                return false;
            }
        }
        return BodyMotion.canMove(overlay, at, exit);
    }

    private static boolean walkReaches(FuzzWorld world, BlockCoord start, BlockCoord target, int maxFall) {
        return walkRoute(world, start, target::equals, maxFall).isPresent();
    }

    private static boolean walkReaches(Overlay world, BlockCoord start, BlockCoord target, int maxFall) {
        return walkRoute(world, start, target::equals, maxFall).isPresent();
    }

    static boolean bodyWalkReachesSky(FuzzWorld world, BlockCoord start) {
        return walkRoute(world, start, world::sky, BodyMotion.MAX_FALL).isPresent();
    }

    private static Optional<List<BlockCoord>> walkRoute(
            RouteWorld world, BlockCoord start, Predicate<BlockCoord> target, int maxFall) {
        if (!BodyMotion.fits(world, start)) {
            return Optional.empty();
        }
        if (target.test(start) && BodyMotion.standable(world, start)) {
            return Optional.of(List.of(start));
        }
        Map<BlockCoord, BlockCoord> parent = new HashMap<>();
        Set<BlockCoord> seen = new HashSet<>();
        Deque<BlockCoord> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        int visits = 0;
        while (!queue.isEmpty() && visits++ < MAX_VISITS) {
            BlockCoord cell = queue.poll();
            for (BlockCoord next : BodyMotion.steps(world, cell, maxFall)) {
                if (!seen.add(next)) {
                    continue;
                }
                parent.put(next, cell);
                if (target.test(next)) {
                    List<BlockCoord> path = new ArrayList<>();
                    for (BlockCoord at = next; at != null; at = parent.get(at)) {
                        path.add(at);
                    }
                    Collections.reverse(path);
                    return Optional.of(path);
                }
                queue.add(next);
            }
        }
        return Optional.empty();
    }
}
