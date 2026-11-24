package com.theminespirit.core.town;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;

public final class TownMapConvert {
    private TownMapConvert() {}

    public static TownMap of(TownPlan plan) {
        Objects.requireNonNull(plan, "План не может быть null: карту экспортируют из него");
        List<TownMap.MapPlot> plots = new ArrayList<>();
        for (Placement place : plan.placements()) {
            plots.add(new TownMap.MapPlot(
                    place.id(),
                    place.id(),
                    place.x(),
                    place.z(),
                    place.x() + place.width() - 1,
                    place.z() + place.depth() - 1,
                    place.facing().id()));
        }
        List<TownMap.MapStreet> streets = new ArrayList<>();
        if (!plan.streets().main().isEmpty()) {
            streets.addAll(rects(plan.streets().main(), "main"));
        }
        if (!plan.streets().side().isEmpty()) {
            streets.addAll(rects(plan.streets().side(), "side"));
        }
        if (!plan.streets().ring().isEmpty()) {
            streets.add(new TownMap.MapStreet("ring", TownMapRules.RING, 0, 0, 0, 0));
        }
        return new TownMap(
                new TownMap.MapPoint(plan.anchor().x(), plan.anchor().z()),
                plots,
                streets,
                segments(rectify(plan.fence().posts(), plan.region())));
    }

    public static List<ColumnMask.Cell> rectify(List<ColumnMask.Cell> posts, ColumnMask region) {
        Objects.requireNonNull(posts, "Столбы стены не могут быть null: диагонали выпрямляются в них");
        Objects.requireNonNull(region, "Граница не может быть null: угол стены ищется внутри неё");
        if (posts.size() < 2) {
            return posts;
        }
        List<ColumnMask.Cell> out = new ArrayList<>();
        for (int index = 0; index < posts.size(); index++) {
            ColumnMask.Cell here = posts.get(index);
            out.add(here);
            ColumnMask.Cell next = posts.get((index + 1) % posts.size());
            if (here.x() != next.x() && here.z() != next.z()) {
                int stepX = Integer.signum(next.x() - here.x());
                ColumnMask.Cell first = new ColumnMask.Cell(here.x() + stepX, here.z());
                ColumnMask.Cell second = new ColumnMask.Cell(here.x(), here.z() + Integer.signum(next.z() - here.z()));
                out.add(region.contains(first.x(), first.z()) ? first : second);
            }
        }
        return List.copyOf(out);
    }

    public static List<TownMap.WallSegment> segments(List<ColumnMask.Cell> posts) {
        Objects.requireNonNull(posts, "Столбы стены не могут быть null: из них собираются отрезки");
        if (posts.size() < 2) {
            return List.of();
        }
        List<Integer> corners = new ArrayList<>();
        for (int index = 0; index < posts.size(); index++) {
            ColumnMask.Cell previous = posts.get((index + posts.size() - 1) % posts.size());
            ColumnMask.Cell here = posts.get(index);
            ColumnMask.Cell next = posts.get((index + 1) % posts.size());
            int inX = Integer.signum(here.x() - previous.x());
            int inZ = Integer.signum(here.z() - previous.z());
            int outX = Integer.signum(next.x() - here.x());
            int outZ = Integer.signum(next.z() - here.z());
            if (inX != outX || inZ != outZ) {
                corners.add(index);
            }
        }
        List<TownMap.WallSegment> out = new ArrayList<>();
        if (corners.isEmpty()) {
            ColumnMask.Cell first = posts.get(0);
            ColumnMask.Cell last = posts.get(posts.size() - 1);
            if (!first.equals(last)) {
                out.add(new TownMap.WallSegment(first.x(), first.z(), last.x(), last.z()));
            }
            return List.copyOf(out);
        }
        for (int index = 0; index < corners.size(); index++) {
            ColumnMask.Cell from = posts.get(corners.get(index));
            ColumnMask.Cell to = posts.get(corners.get((index + 1) % corners.size()));
            out.add(new TownMap.WallSegment(from.x(), from.z(), to.x(), to.z()));
        }
        return List.copyOf(out);
    }

    private static List<TownMap.MapStreet> rects(ColumnMask mask, String kind) {
        BitSet visited = new BitSet(mask.width() * mask.depth());
        List<TownMap.MapStreet> out = new ArrayList<>();
        for (ColumnMask.Cell cell : mask.cells()) {
            int index = (cell.z() - mask.minZ()) * mask.width() + cell.x() - mask.minX();
            if (visited.get(index)) {
                continue;
            }
            int x1 = cell.x();
            int z1 = cell.z();
            int x2 = x1;
            while (mask.contains(x2 + 1, z1)) {
                x2++;
            }
            int z2 = z1;
            while (row(mask, x1, x2, z2 + 1)) {
                z2++;
            }
            for (int x = x1; x <= x2; x++) {
                for (int z = z1; z <= z2; z++) {
                    visited.set((z - mask.minZ()) * mask.width() + x - mask.minX());
                }
            }
            out.add(new TownMap.MapStreet(kind + "-" + out.size(), kind, x1, z1, x2, z2));
        }
        return out;
    }

    private static boolean row(ColumnMask mask, int x1, int x2, int z) {
        for (int x = x1; x <= x2; x++) {
            if (!mask.contains(x, z)) {
                return false;
            }
        }
        return true;
    }
}
