package com.theminespirit.core.town;

import com.theminespirit.core.board.StageRules;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
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
        TerrainGrid grid = grid(feet, water);
        ColumnMask region = ColumnMask.rect(
                ANCHOR.x() - RADIUS + 2, ANCHOR.z() - RADIUS + 2, ANCHOR.x() + RADIUS - 2, ANCHOR.z() + RADIUS - 2);
        TownStreets streets = layStreets(grid, region, ANCHOR.x(), ANCHOR.z(), RULES.streets());
        List<Placement> placed = new ArrayList<>();
        for (int stage = 1; stage <= STAGES.size(); stage++) {
            for (String id : STAGES.get(stage - 1)) {
                if (TownSnapshot.FENCE.equals(id) || TownSnapshot.TORCHES.equals(id)) {
                    continue;
                }
                TownEntry entry = RULES.entry(id)
                        .orElseThrow(() -> new IllegalArgumentException("Нет постройки '" + id + "' в каталоге"));
                placed.add(place(grid, region, streets, placed, id, entry.name(), entry.width(), entry.depth(), stage));
            }
        }
        List<ColumnMask.Cell> loop = FenceLine.loop(region);
        List<Integer> gates = new ArrayList<>();
        for (int index = 0; index < loop.size(); index++) {
            if (streets.contains(loop.get(index).x(), loop.get(index).z())) {
                gates.add(index);
            }
        }
        return new TownPlan(ANCHOR, region, streets, placed, new FenceLine(loop, gates), List.of());
    }

    private static TownStreets layStreets(
            TerrainGrid grid, ColumnMask region, int centerX, int centerZ, TownRules.Streets rules) {
        int size = region.width() * region.depth();
        BitSet main = new BitSet(size);
        BitSet side = new BitSet(size);
        int mainHalf = rules.main() / 2;
        int sideHalf = rules.side() / 2;
        for (int index = 0; index < size; index++) {
            int x = region.minX() + index % region.width();
            int z = region.minZ() + index / region.width();
            if (grid.water(x, z)) {
                continue;
            }
            if (Math.abs(x - centerX) <= mainHalf || Math.abs(z - centerZ) <= mainHalf) {
                main.set(index);
            } else if (onLine(x - centerX, rules, sideHalf) || onLine(z - centerZ, rules, sideHalf)) {
                side.set(index);
            }
        }
        return new TownStreets(mask(region, main), mask(region, side));
    }

    private static boolean onLine(int offset, TownRules.Streets rules, int half) {
        int distance = Math.abs(offset);
        int nearest = Math.round((float) distance / rules.blockSize()) * rules.blockSize();
        return nearest > 0 && Math.abs(distance - nearest) <= half;
    }

    private static ColumnMask mask(ColumnMask region, BitSet bits) {
        return new ColumnMask(region.minX(), region.minZ(), region.width(), region.depth(), bits);
    }

    private static Placement place(
            TerrainGrid grid,
            ColumnMask region,
            TownStreets streets,
            List<Placement> placed,
            String id,
            String name,
            int width,
            int depth,
            int stage) {
        int gap = RULES.spacing().gap();
        int maxX = region.minX() + region.width() - width;
        int maxZ = region.minZ() + region.depth() - depth;
        for (int z = region.minZ(); z <= maxZ; z++) {
            for (int x = region.minX(); x <= maxX; x++) {
                if (free(grid, region, streets, placed, x, z, width, depth, gap)) {
                    return new Placement(
                            id, name, stage, x, z, width, depth, facing(region, streets, x, z, width, depth));
                }
            }
        }
        throw new IllegalArgumentException("Тестовая раскладка не вместила " + id + ": площадь контура мала");
    }

    private static boolean free(
            TerrainGrid grid,
            ColumnMask region,
            TownStreets streets,
            List<Placement> placed,
            int x,
            int z,
            int width,
            int depth,
            int gap) {
        for (int dz = 0; dz < depth; dz++) {
            for (int dx = 0; dx < width; dx++) {
                int px = x + dx;
                int pz = z + dz;
                if (!region.contains(px, pz)
                        || px <= region.minX()
                        || pz <= region.minZ()
                        || px >= region.minX() + region.width() - 1
                        || pz >= region.minZ() + region.depth() - 1
                        || grid.feet(px, pz) == TerrainGrid.NO_GROUND
                        || grid.water(px, pz)
                        || streets.contains(px, pz)) {
                    return false;
                }
            }
        }
        return placed.stream()
                .noneMatch(other -> x - gap < other.x() + other.width()
                        && other.x() - gap < x + width
                        && z - gap < other.z() + other.depth()
                        && other.z() - gap < z + depth);
    }

    private static Placement.Facing facing(ColumnMask region, TownStreets streets, int x, int z, int width, int depth) {
        int centerX = x + width / 2;
        int centerZ = z + depth / 2;
        int limit = region.width() + region.depth();
        int step = 0;
        while (step < limit && !streets.contains(centerX + step, centerZ)) {
            step++;
        }
        int east = step;
        step = 0;
        while (step < limit && !streets.contains(centerX - step, centerZ)) {
            step++;
        }
        int west = step;
        step = 0;
        while (step < limit && !streets.contains(centerX, centerZ + step)) {
            step++;
        }
        int south = step;
        step = 0;
        while (step < limit && !streets.contains(centerX, centerZ - step)) {
            step++;
        }
        int north = step;
        int best = Math.min(Math.min(east, west), Math.min(south, north));
        if (best == east) {
            return Placement.Facing.EAST;
        }
        if (best == west) {
            return Placement.Facing.WEST;
        }
        return best == south ? Placement.Facing.SOUTH : Placement.Facing.NORTH;
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

    public static StageSite flatSite(String wood) {
        return StageSites.of(FLAT, 1, LEVEL, TownRules.load(), StageSites.ORCHARD_SCHEME, wood);
    }

    public static TownPlan hillyPlan(long seed) {
        Relief relief = new Relief(seed);
        return plan(relief::feet, relief::water);
    }

    public static StageSite site(TownPlan plan) {
        return StageSites.of(plan, 1, plan.anchor().y(), TownRules.load());
    }

    public static TownPlan planWithOrchard(String building) {
        TownPlan base = flatPlan();
        TownEntry template = RULES.template(OrchardSites.TEMPLATE)
                .orElseThrow(() -> new IllegalArgumentException("Нет шаблона '" + OrchardSites.TEMPLATE + "'"));
        List<Placement> placed = new ArrayList<>(base.placements());
        placed.add(place(
                grid((x, z) -> LEVEL, (x, z) -> 0),
                base.region(),
                base.streets(),
                placed,
                building,
                template.name(),
                template.width(),
                template.depth(),
                1));
        return new TownPlan(
                base.anchor(), base.region(), base.streets(), placed, base.fence(), base.unplaced(), base.paths());
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
