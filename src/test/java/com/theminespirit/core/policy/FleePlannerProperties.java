package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class FleePlannerProperties {
    private static final int SIDE = 13;
    private static final int MAX_HEIGHT = 4;

    private static final class Grid implements SafetyView {
        private final int[] top = new int[SIDE * SIDE];
        private final boolean[] lava = new boolean[SIDE * SIDE];
        private int reads;

        Grid(Random random) {
            for (int i = 0; i < top.length; i++) {
                top[i] = 1 + random.nextInt(MAX_HEIGHT);
                lava[i] = random.nextInt(12) == 0;
            }
        }

        int top(int x, int z) {
            return top[x * SIDE + z];
        }

        Cell peek(BlockCoord at) {
            if (at.x() < 0 || at.z() < 0 || at.x() >= SIDE || at.z() >= SIDE) {
                return Cell.SOLID;
            }
            int height = top(at.x(), at.z());
            if (at.y() < height) {
                return Cell.SOLID;
            }
            if (at.y() == height && lava[at.x() * SIDE + at.z()]) {
                return Cell.LAVA;
            }
            return Cell.PASSABLE;
        }

        boolean standing(BlockCoord at) {
            return peek(at) == Cell.PASSABLE
                    && peek(new BlockCoord(at.x(), at.y() + 1, at.z())) == Cell.PASSABLE
                    && peek(new BlockCoord(at.x(), at.y() - 1, at.z())) == Cell.SOLID;
        }

        int reads() {
            return reads;
        }

        @Override
        public Cell cellAt(BlockCoord coord) {
            reads++;
            return peek(coord);
        }
    }

    private static Set<BlockCoord> reachableByLooseRules(Grid grid, BlockCoord origin) {
        Set<BlockCoord> seen = new HashSet<>();
        Queue<BlockCoord> queue = new ArrayDeque<>();
        seen.add(origin);
        queue.add(origin);
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            BlockCoord cell = queue.poll();
            for (int[] step : steps) {
                for (int y = cell.y() - 2; y <= cell.y() + 1; y++) {
                    BlockCoord next = new BlockCoord(cell.x() + step[0], y, cell.z() + step[1]);
                    if (grid.standing(next) && seen.add(next)) {
                        queue.add(next);
                    }
                }
            }
        }
        return seen;
    }

    private static BlockCoord randomStanding(Grid grid, Random random) {
        for (int attempt = 0; attempt < 200; attempt++) {
            int x = random.nextInt(SIDE);
            int z = random.nextInt(SIDE);
            BlockCoord feet = new BlockCoord(x, grid.top(x, z), z);
            if (grid.standing(feet)) {
                return feet;
            }
        }
        return null;
    }

    private static List<FleePlanner.Threat> randomThreats(Random random) {
        List<FleePlanner.Threat> threats = new ArrayList<>();
        int count = 1 + random.nextInt(3);
        for (int i = 0; i < count; i++) {
            BlockCoord at = new BlockCoord(random.nextInt(SIDE), 1 + random.nextInt(MAX_HEIGHT), random.nextInt(SIDE));
            threats.add(new FleePlanner.Threat(at, 3 + random.nextInt(7)));
        }
        return threats;
    }

    private static double worstSlack(BlockCoord cell, List<FleePlanner.Threat> threats) {
        double worst = Double.POSITIVE_INFINITY;
        for (FleePlanner.Threat threat : threats) {
            worst = Math.min(worst, threat.slack(cell));
        }
        return worst;
    }

    @Property(tries = 200)
    void plan_RandomTerrain_TargetIsReachableAndNotWorseThanStart(
            @ForAll @IntRange(min = 0, max = 1_000_000) int seed) {
        Random random = new Random(seed);
        Grid grid = new Grid(random);
        BlockCoord origin = randomStanding(grid, random);
        if (origin == null) {
            return;
        }
        List<FleePlanner.Threat> threats = randomThreats(random);

        FleePlanner.Plan plan = FleePlanner.plan(origin, threats, grid);

        if (plan.action() instanceof ReflexAction.Flee flee) {
            assertTrue(
                    reachableByLooseRules(grid, origin).contains(flee.cell()),
                    "Цель бегства обязана быть достижима шагами по земле (вверх на 1, вниз на 2), иначе дух упирается в стену. Цель: "
                            + flee.cell());
            assertTrue(
                    worstSlack(flee.cell(), threats) >= Math.min(0, worstSlack(origin, threats)),
                    "Цель бегства либо выполняет все расстояния, либо не опаснее начала: иначе дух убегает в сторону угрозы. Цель: "
                            + flee.cell());
            assertTrue(
                    Math.abs(flee.cell().x() - origin.x()) <= 8
                            && Math.abs(flee.cell().z() - origin.z()) <= 8,
                    "Цель лежит в радиусе поиска 8. Цель: " + flee.cell());
        }
    }

    @Property(tries = 200)
    void plan_RandomTerrain_ReadsAndVisitedStayWithinBudget(@ForAll @IntRange(min = 0, max = 1_000_000) int seed) {
        Random random = new Random(seed);
        Grid grid = new Grid(random);
        BlockCoord origin = randomStanding(grid, random);
        if (origin == null) {
            return;
        }
        List<FleePlanner.Threat> threats =
                List.of(new FleePlanner.Threat(new BlockCoord(random.nextInt(SIDE), 2, random.nextInt(SIDE)), 40));

        FleePlanner.Plan plan = FleePlanner.plan(origin, threats, grid, 50, 8);

        assertTrue(plan.visited() <= 50, "Посещено не более maxNodes = 50. Посещено: " + plan.visited());
        assertTrue(
                grid.reads() <= 300,
                "Чтений SafetyView не более maxNodes × 6 = 300: иначе тик духа зависит от рельефа. Прочитано: "
                        + grid.reads());
    }

    @Property(tries = 200)
    void plan_SameInputsTwice_GiveEqualPlans(@ForAll @IntRange(min = 0, max = 1_000_000) int seed) {
        Random random = new Random(seed);
        Grid grid = new Grid(random);
        BlockCoord origin = randomStanding(grid, random);
        if (origin == null) {
            return;
        }
        List<FleePlanner.Threat> threats = randomThreats(random);

        FleePlanner.Plan first = FleePlanner.plan(origin, threats, grid);
        FleePlanner.Plan second = FleePlanner.plan(origin, threats, grid);

        assertEquals(
                first,
                second,
                "Порядок обхода зафиксирован: одинаковые входы дают один и тот же план, иначе дух мечется между двумя целями");
    }
}
