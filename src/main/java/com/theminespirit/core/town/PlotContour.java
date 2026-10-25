package com.theminespirit.core.town;

import java.util.BitSet;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Queue;

public final class PlotContour {
    private static final int[] DX = {1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 1, -1};

    private PlotContour() {}

    public static ColumnMask grow(TerrainGrid grid, int anchorX, int anchorZ, TownRules.Ground rules) {
        Objects.requireNonNull(grid, "Рельеф не может быть null: контур растёт по нему");
        Objects.requireNonNull(rules, "Правила рельефа не могут быть null: площадь и обрыв — данные");
        if (!grid.land(anchorX, anchorZ)) {
            throw new IllegalArgumentException("Якорь " + anchorX + "," + anchorZ
                    + " не на суше: дом духов в воде или над пустотой — участок от него не разметить");
        }
        GridBits bits = new GridBits(grid.width(), grid.depth());
        BitSet land = new BitSet(bits.size());
        for (int z = 0; z < grid.depth(); z++) {
            for (int x = 0; x < grid.width(); x++) {
                if (grid.land(grid.minX() + x, grid.minZ() + z)) {
                    land.set(bits.at(x, z));
                }
            }
        }
        int start = bits.at(anchorX - grid.minX(), anchorZ - grid.minZ());
        BitSet region = flood(grid, bits, land, start, rules);
        BitSet closed = bits.erode(bits.dilate(region));
        closed.and(land);
        closed.or(region);
        BitSet opened = bits.dilate(bits.erode(closed));
        opened.and(closed);
        BitSet kept = bits.component(opened, start);
        if (kept.isEmpty()) {
            kept = bits.component(region, start);
        }
        return new ColumnMask(grid.minX(), grid.minZ(), grid.width(), grid.depth(), bits.fillHoles(kept));
    }

    private static BitSet flood(TerrainGrid grid, GridBits bits, BitSet land, int start, TownRules.Ground rules) {
        int ax = start % grid.width();
        int az = start / grid.width();
        int anchorFeet = grid.feet(grid.minX() + ax, grid.minZ() + az);
        BitSet region = new BitSet(bits.size());
        BitSet seen = new BitSet(bits.size());
        Queue<long[]> queue =
                new PriorityQueue<>((a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0]) : Long.compare(a[1], b[1]));
        queue.add(new long[] {0, start});
        seen.set(start);
        while (!queue.isEmpty() && region.cardinality() < rules.area()) {
            int index = (int) queue.poll()[1];
            region.set(index);
            int x = index % grid.width();
            int z = index / grid.width();
            int here = grid.feet(grid.minX() + x, grid.minZ() + z);
            for (int side = 0; side < DX.length; side++) {
                int nx = x + DX[side];
                int nz = z + DZ[side];
                if (!bits.inside(nx, nz) || seen.get(bits.at(nx, nz)) || !land.get(bits.at(nx, nz))) {
                    continue;
                }
                int there = grid.feet(grid.minX() + nx, grid.minZ() + nz);
                if (Math.abs(there - here) > rules.cliffStep()) {
                    continue;
                }
                seen.set(bits.at(nx, nz));
                long rise = (long) there - anchorFeet;
                long cost =
                        (long) (nx - ax) * (nx - ax) + (long) (nz - az) * (nz - az) + rules.slopeWeight() * rise * rise;
                queue.add(new long[] {cost, bits.at(nx, nz)});
            }
        }
        return region;
    }
}
