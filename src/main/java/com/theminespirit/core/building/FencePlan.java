package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.CatalogLimits;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public record FencePlan(List<BlockCoord> cells, BlockCoord gate) {
    public FencePlan {
        Objects.requireNonNull(cells, "Клетки забора не могут быть null: пустой забор задают пустым списком");
        Objects.requireNonNull(gate, "Клетка калитки не может быть null: у забора всегда есть проход");
        cells = List.copyOf(cells);
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("Забор без клеток: после калитки не осталось ни одного блока");
        }
    }

    public static FencePlan contour(List<BlockCoord> corners) {
        Objects.requireNonNull(corners, "Контур забора не может быть null: без углов неизвестно, где ставить забор");
        List<BlockCoord> points = normalized(corners);
        if (points.size() < CatalogLimits.FENCE_CORNERS_MIN
                || points.size() > CatalogLimits.FENCE_CORNERS_MAX) {
            throw new IllegalArgumentException("Углов контура " + points.size() + ": нужно от "
                    + CatalogLimits.FENCE_CORNERS_MIN + " до " + CatalogLimits.FENCE_CORNERS_MAX
                    + ", иначе не задать замкнутый забор");
        }
        int y = points.get(0).y();
        for (BlockCoord point : points) {
            if (point.y() != y) {
                throw new IllegalArgumentException("Угол контура " + point + " на высоте " + point.y()
                        + ", а первый на " + y + ": забор стоит на одном уровне, наклонный не ставится");
            }
        }
        List<List<BlockCoord>> edges = new ArrayList<>();
        List<Integer> lengths = new ArrayList<>();
        for (int index = 0; index < points.size(); index++) {
            BlockCoord from = points.get(index);
            BlockCoord to = points.get((index + 1) % points.size());
            List<BlockCoord> edge = edgeCells(from, to);
            edges.add(edge);
            lengths.add(edge.size());
        }
        int longest = 0;
        for (int index = 1; index < lengths.size(); index++) {
            if (lengths.get(index) > lengths.get(longest)) {
                longest = index;
            }
        }
        List<BlockCoord> all = new ArrayList<>();
        for (List<BlockCoord> edge : edges) {
            all.addAll(edge);
        }
        List<BlockCoord> distinct = List.copyOf(new LinkedHashSet<>(all));
        if (distinct.size() > CatalogLimits.FENCE_BLOCKS_MAX) {
            throw new IllegalArgumentException("Клеток забора " + distinct.size() + " больше предела "
                    + CatalogLimits.FENCE_BLOCKS_MAX + ": разбейте забор на части");
        }
        BlockCoord gate = edges.get(longest).get(lengths.get(longest) / 2);
        List<BlockCoord> cells = new ArrayList<>(distinct);
        cells.remove(gate);
        return new FencePlan(cells, gate);
    }

    private static List<BlockCoord> normalized(List<BlockCoord> corners) {
        List<BlockCoord> points = new ArrayList<>();
        for (BlockCoord corner : corners) {
            Objects.requireNonNull(corner, "Угол контура не может быть null: без точки не построить ребро");
            if (points.isEmpty() || !points.get(points.size() - 1).equals(corner)) {
                points.add(corner);
            }
        }
        if (points.size() > 1 && points.get(0).equals(points.get(points.size() - 1))) {
            points.remove(points.size() - 1);
        }
        return points;
    }

    private static List<BlockCoord> edgeCells(BlockCoord from, BlockCoord to) {
        int dx = to.x() - from.x();
        int dz = to.z() - from.z();
        if (dx != 0 && dz != 0) {
            throw new IllegalArgumentException("Ребро контура " + from + " → " + to + " идёт по диагонали: "
                    + "забор ставится по осям, иначе между столбами остаются дыры");
        }
        int length = Math.max(Math.abs(dx), Math.abs(dz));
        if (length < 1) {
            throw new IllegalArgumentException("Ребро контура " + from + " → " + to + " нулевой длины: "
                    + "углы обязаны различаться");
        }
        int stepX = Integer.signum(dx);
        int stepZ = Integer.signum(dz);
        List<BlockCoord> cells = new ArrayList<>(length);
        for (int step = 0; step < length; step++) {
            cells.add(new BlockCoord(from.x() + stepX * step, from.y(), from.z() + stepZ * step));
        }
        return cells;
    }
}
