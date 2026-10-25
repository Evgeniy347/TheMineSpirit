package com.theminespirit.core.town;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class BuildingPlacer {
    private static final long QUARTER_PENALTY = 1_000_000L;
    private static final List<Placement.Facing> SIDES =
            List.of(Placement.Facing.NORTH, Placement.Facing.EAST, Placement.Facing.SOUTH, Placement.Facing.WEST);
    private final TerrainGrid grid;
    private final ColumnMask region;
    private final TownStreets streets;
    private final int centerX;
    private final int centerZ;
    private final TownRules rules;
    private final int[] feet;
    private final int[] streetSum;
    private final List<Placement> placed = new ArrayList<>();
    private final Map<Integer, Placement> seeds = new HashMap<>();

    BuildingPlacer(
            TerrainGrid grid, ColumnMask region, TownStreets streets, int centerX, int centerZ, TownRules rules) {
        this.grid = grid;
        this.region = region;
        this.streets = streets;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.rules = rules;
        int size = grid.width() * grid.depth();
        this.feet = new int[size];
        int[] street = new int[size];
        for (int z = 0; z < grid.depth(); z++) {
            for (int x = 0; x < grid.width(); x++) {
                int wx = grid.minX() + x;
                int wz = grid.minZ() + z;
                feet[z * grid.width() + x] = grid.land(wx, wz) ? grid.feet(wx, wz) : 0;
                street[z * grid.width() + x] = streets.contains(wx, wz) ? 1 : 0;
            }
        }
        this.streetSum = prefix(street);
    }

    List<Placement> placed() {
        return List.copyOf(placed);
    }

    Optional<Placement> place(TownEntry entry, int stage) {
        Objects.requireNonNull(entry, "Постройка не может быть null");
        Optional<Placement> chosen =
                entry.quarter() == TownEntry.Quarter.CENTER ? centered(entry, stage) : best(entry, stage);
        chosen.ifPresent(found -> {
            placed.add(found);
            seeds.putIfAbsent(stage, found);
        });
        return chosen;
    }

    private Optional<Placement> centered(TownEntry entry, int stage) {
        int x = centerX - entry.width() / 2;
        int z = centerZ - entry.depth() / 2;
        Placement candidate = new Placement(
                entry.id(), entry.name(), stage, x, z, entry.width(), entry.depth(), Placement.Facing.SOUTH);
        for (int px = x; px < x + entry.width(); px++) {
            for (int pz = z; pz < z + entry.depth(); pz++) {
                if (!region.contains(px, pz) || grid.water(px, pz)) {
                    return Optional.empty();
                }
            }
        }
        return placed.stream()
                        .anyMatch(other ->
                                other.overlaps(candidate, rules.spacing().gap()))
                ? Optional.empty()
                : Optional.of(candidate);
    }

    private Optional<Placement> best(TownEntry entry, int stage) {
        int[] blockedSum = prefix(blocked());
        Placement seed = seeds.get(stage);
        int targetX = seed == null ? centerX : seed.centerX();
        int targetZ = seed == null ? centerZ : seed.centerZ();
        Placement best = null;
        long bestScore = Long.MAX_VALUE;
        boolean square = entry.width() == entry.depth();
        for (int turn = 0; turn < (entry.rotate() && !square ? 2 : 1); turn++) {
            int w = turn == 0 ? entry.width() : entry.depth();
            int d = turn == 0 ? entry.depth() : entry.width();
            int[] spread = SlidingRange.spread(feet, grid.width(), grid.depth(), w, d);
            for (int z = 1; z + d < grid.depth(); z++) {
                for (int x = 1; x + w < grid.width(); x++) {
                    if (sum(blockedSum, x, z, w, d) > 0
                            || spread[z * grid.width() + x] > rules.ground().slopeMax()) {
                        continue;
                    }
                    Optional<Placement.Facing> facing = facing(x, z, w, d);
                    if (facing.isEmpty()) {
                        continue;
                    }
                    int wx = grid.minX() + x;
                    int wz = grid.minZ() + z;
                    long dx = (long) wx + w / 2 - targetX;
                    long dz = (long) wz + d / 2 - targetZ;
                    long slope = spread[z * grid.width() + x];
                    boolean inQuarter = entry.quarter().holds(wx + w / 2 - centerX, wz + d / 2 - centerZ);
                    long score = dx * dx
                            + dz * dz
                            + rules.ground().slopeWeight() * slope * slope
                            + (inQuarter ? 0 : QUARTER_PENALTY);
                    if (score < bestScore) {
                        bestScore = score;
                        best = new Placement(entry.id(), entry.name(), stage, wx, wz, w, d, facing.orElseThrow());
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private Optional<Placement.Facing> facing(int x, int z, int w, int d) {
        int north = sum(streetSum, x, z - 1, w, 1);
        int[] touch = {
            north, sum(streetSum, x + w, z, 1, d), sum(streetSum, x, z + d, w, 1), sum(streetSum, x - 1, z, 1, d)
        };
        int side = 0;
        for (int index = 1; index < touch.length; index++) {
            if (touch[index] > touch[side]) {
                side = index;
            }
        }
        return touch[side] > 0 ? Optional.of(SIDES.get(side)) : Optional.empty();
    }

    private int[] blocked() {
        int[] blocked = new int[grid.width() * grid.depth()];
        int gap = rules.spacing().gap();
        for (int z = 0; z < grid.depth(); z++) {
            for (int x = 0; x < grid.width(); x++) {
                int wx = grid.minX() + x;
                int wz = grid.minZ() + z;
                boolean free = region.contains(wx, wz) && grid.land(wx, wz) && !streets.contains(wx, wz);
                blocked[z * grid.width() + x] = free ? 0 : 1;
            }
        }
        for (Placement other : placed) {
            for (int wz = other.z() - gap; wz < other.z() + other.depth() + gap; wz++) {
                for (int wx = other.x() - gap; wx < other.x() + other.width() + gap; wx++) {
                    if (grid.inside(wx, wz)) {
                        blocked[(wz - grid.minZ()) * grid.width() + wx - grid.minX()] = 1;
                    }
                }
            }
        }
        return blocked;
    }

    private int[] prefix(int[] cells) {
        int w = grid.width() + 1;
        int[] sum = new int[w * (grid.depth() + 1)];
        for (int z = 0; z < grid.depth(); z++) {
            for (int x = 0; x < grid.width(); x++) {
                sum[(z + 1) * w + x + 1] =
                        cells[z * grid.width() + x] + sum[z * w + x + 1] + sum[(z + 1) * w + x] - sum[z * w + x];
            }
        }
        return sum;
    }

    private int sum(int[] prefix, int x, int z, int w, int d) {
        int x1 = Math.max(0, x);
        int z1 = Math.max(0, z);
        int x2 = Math.min(grid.width(), x + w);
        int z2 = Math.min(grid.depth(), z + d);
        if (x1 >= x2 || z1 >= z2) {
            return 0;
        }
        int stride = grid.width() + 1;
        return prefix[z2 * stride + x2]
                - prefix[z1 * stride + x2]
                - prefix[z2 * stride + x1]
                + prefix[z1 * stride + x1];
    }
}
