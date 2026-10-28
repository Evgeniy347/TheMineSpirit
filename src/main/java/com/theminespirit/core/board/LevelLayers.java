package com.theminespirit.core.board;

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
