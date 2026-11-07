package com.theminespirit.core.town;

import com.theminespirit.core.board.StageRules;
import com.theminespirit.core.observation.BlockCoord;
import java.util.BitSet;
import java.util.List;
import java.util.function.IntBinaryOperator;

public final class TownFixtures {
    public static final int LEVEL = 70;
    private static final TownRules RULES = TownRules.load();
    private static final List<List<String>> STAGES = StageRules.load().plans();
    private static final BlockCoord ANCHOR = new BlockCoord(200, 70, -300);
    private static final int RADIUS = RULES.ground().probeRadius();
    private static final TownPlan FLAT = plan((x, z) -> LEVEL, (x, z) -> 0);

    private TownFixtures() {}

    public static TownPlan plan(IntBinaryOperator feet, IntBinaryOperator water) {
        return TownPlanner.plan(grid(feet, water), ANCHOR, RULES, STAGES);
    }

    public static TerrainGrid grid(IntBinaryOperator feet, IntBinaryOperator water) {
        int side = 2 * RADIUS + 1;
        int[] heights = new int[side * side];
        BitSet wet = new BitSet(side * side);
        for (int dz = -RADIUS; dz <= RADIUS; dz++) {
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                int x = ANCHOR.x() + dx;
                int z = ANCHOR.z() + dz;
                heights[TerrainGrid.index(RADIUS, dx, dz)] = feet.applyAsInt(x, z);
                wet.set(TerrainGrid.index(RADIUS, dx, dz), water.applyAsInt(x, z) != 0);
            }
        }
        return TerrainGrid.around(ANCHOR, RADIUS, heights, wet);
    }

    public static TownPlan flatPlan() {
        return FLAT;
    }

    public static StageSite flatSite() {
        return StageSites.of(FLAT, 1, LEVEL, TownRules.load());
    }

    public static TownPlan hillyPlan(long seed) {
        Relief relief = new Relief(seed);
        return plan(relief::feet, relief::water);
    }

    public static StageSite site(TownPlan plan) {
        return StageSites.of(plan, 1, plan.anchor().y(), TownRules.load());
    }

    public static TownPlan planWithOrchard(String building) {
        TownRules rules = TownRules.load();
        return TownPlanner.plant(
                grid((x, z) -> LEVEL, (x, z) -> 0),
                flatPlan(),
                rules,
                StageRules.load().plans(),
                List.of(OrchardSites.siteId(building)),
                1);
    }

    private record Relief(long seed) {
        private static final int CELL = 24;
        private static final int AMPLITUDE = 9;
        private static final int SEA = LEVEL - 6;

        int feet(int x, int z) {
            return LEVEL + (int) Math.round(AMPLITUDE * (noise(x, z, CELL) - 0.5) * 2);
        }

        int water(int x, int z) {
            return feet(x, z) < SEA ? 1 : 0;
        }

        private double noise(int x, int z, int cell) {
            int gx = Math.floorDiv(x, cell);
            int gz = Math.floorDiv(z, cell);
            double fx = smooth((x - gx * (double) cell) / cell);
            double fz = smooth((z - gz * (double) cell) / cell);
            double top = lerp(corner(gx, gz), corner(gx + 1, gz), fx);
            double bottom = lerp(corner(gx, gz + 1), corner(gx + 1, gz + 1), fx);
            return lerp(top, bottom, fz);
        }

        private double corner(int gx, int gz) {
            long h = seed * 0x9E3779B97F4A7C15L + gx * 0xC2B2AE3D27D4EB4FL + gz * 0x165667B19E3779F9L;
            h ^= h >>> 33;
            h *= 0xFF51AFD7ED558CCDL;
            h ^= h >>> 33;
            return (h >>> 11) / (double) (1L << 53);
        }

        private static double smooth(double t) {
            return t * t * (3 - 2 * t);
        }

        private static double lerp(double a, double b, double t) {
            return a + (b - a) * t;
        }
    }
}
