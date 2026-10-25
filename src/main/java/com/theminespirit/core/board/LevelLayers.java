package com.theminespirit.core.board;

import com.theminespirit.core.town.ColumnMask;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class LevelLayers {
    private LevelLayers() {}

    public static List<PlotLeveling.Box> layers(List<PlotLeveling.Box> fills) {
        Objects.requireNonNull(fills, "Ящики подсыпки не могут быть null");
        List<PlotLeveling.Box> out = new ArrayList<>();
        int bottom = fills.stream().mapToInt(PlotLeveling.Box::y1).min().orElse(0);
        int top = fills.stream().mapToInt(PlotLeveling.Box::y2).max().orElse(-1);
        for (int y = bottom; y <= top; y++) {
            int level = y;
            List<PlotLeveling.Box> rows = fills.stream()
                    .filter(box -> box.y1() <= level && level <= box.y2())
                    .map(box -> new PlotLeveling.Box(box.x1(), level, box.z1(), box.x2(), level, box.z2()))
                    .toList();
            out.addAll(merged(rows));
        }
        return out;
    }

    public static List<PlotLeveling.Box> rectangles(ColumnMask mask, int y1, int y2) {
        Objects.requireNonNull(mask, "Маска площадок не может быть null");
        List<PlotLeveling.Box> rows = new ArrayList<>();
        for (int z = mask.minZ(); z < mask.minZ() + mask.depth(); z++) {
            int start = Integer.MIN_VALUE;
            for (int x = mask.minX(); x <= mask.minX() + mask.width(); x++) {
                boolean in = x < mask.minX() + mask.width() && mask.contains(x, z);
                if (in && start == Integer.MIN_VALUE) {
                    start = x;
                } else if (!in && start != Integer.MIN_VALUE) {
                    rows.add(new PlotLeveling.Box(start, y1, z, x - 1, y2, z));
                    start = Integer.MIN_VALUE;
                }
            }
        }
        return merged(rows);
    }

    private static List<PlotLeveling.Box> merged(List<PlotLeveling.Box> rows) {
        List<PlotLeveling.Box> sorted = rows.stream()
                .sorted(Comparator.comparingInt(PlotLeveling.Box::x1)
                        .thenComparingInt(PlotLeveling.Box::x2)
                        .thenComparingInt(PlotLeveling.Box::z1))
                .toList();
        List<PlotLeveling.Box> out = new ArrayList<>();
        PlotLeveling.Box open = null;
        for (PlotLeveling.Box row : sorted) {
            if (open != null && open.x1() == row.x1() && open.x2() == row.x2() && open.z2() + 1 == row.z1()) {
                open = new PlotLeveling.Box(open.x1(), open.y1(), open.z1(), open.x2(), open.y2(), row.z2());
                continue;
            }
            if (open != null) {
                out.add(open);
            }
            open = row;
        }
        if (open != null) {
            out.add(open);
        }
        return out;
    }
}
