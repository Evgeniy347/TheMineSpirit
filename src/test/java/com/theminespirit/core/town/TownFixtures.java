package com.theminespirit.core.town;

public final class TownFixtures {
    public static final int LEVEL = 70;
    private static final TownPlan FLAT = TownPlannerTests.plan((x, z) -> LEVEL, (x, z) -> 0);

    private TownFixtures() {}

    public static TownPlan flatPlan() {
        return FLAT;
    }

    public static StageSite flatSite() {
        return StageSites.of(FLAT, 1, LEVEL, TownRules.load());
    }

    public static TownPlan hillyPlan(long seed) {
        Relief relief = new Relief(seed);
        return TownPlannerTests.plan(relief::feet, relief::water);
    }

    public static StageSite site(TownPlan plan) {
        return StageSites.of(plan, 1, plan.anchor().y(), TownRules.load());
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
