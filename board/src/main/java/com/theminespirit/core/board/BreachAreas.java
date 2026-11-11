package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

public final class BreachAreas {
    public record Cell(BlockCoord at, String blockId) {
        public Cell {
            Objects.requireNonNull(at, "Клетка пролома не может быть null: без неё нечего заделывать");
            Objects.requireNonNull(blockId, "Блок пролома не может быть null: клетку возвращают прежним блоком");
        }
    }

    public record Area(String blockId, int x1, int y1, int z1, int x2, int y2, int z2) {
        public Area {
            Objects.requireNonNull(blockId, "Блок площади не может быть null: без него fill не знает, что ставить");
            if (x2 < x1 || y2 < y1 || z2 < z1) {
                throw new IllegalArgumentException("Площадь " + x1 + ".." + x2 + ", " + y1 + ".." + y2 + ", " + z1
                        + ".." + z2 + " перевёрнута: границы идут от меньшей к большей");
            }
        }

        public BlockCoord min() {
            return new BlockCoord(x1, y1, z1);
        }

        public int cells() {
            return (x2 - x1 + 1) * (y2 - y1 + 1) * (z2 - z1 + 1);
        }
    }

    private BreachAreas() {}

    public static List<Area> group(List<Cell> cells, int limit) {
        Objects.requireNonNull(cells, "Клетки проломов не могут быть null: пусто — пустой список");
        if (limit < 1) {
            throw new IllegalArgumentException("Предел клеток площади " + limit + " меньше 1: группа не построится");
        }
        Map<Layer, NavigableMap<Integer, Map<Integer, Integer>>> runs = new LinkedHashMap<>();
        for (Cell cell : cells) {
            BlockCoord at = cell.at();
            runs.computeIfAbsent(new Layer(cell.blockId(), at.y()), key -> new TreeMap<>())
                    .computeIfAbsent(at.z(), key -> new TreeMap<>())
                    .put(at.x(), 1);
        }
        List<Area> areas = new ArrayList<>();
        runs.forEach((layer, rows) -> areas.addAll(areas(layer, rows, limit)));
        areas.sort(Comparator.comparingInt(Area::y1)
                .thenComparingInt(Area::z1)
                .thenComparingInt(Area::x1)
                .thenComparing(Area::blockId));
        return List.copyOf(areas);
    }

    private static List<Area> areas(Layer layer, NavigableMap<Integer, Map<Integer, Integer>> rows, int limit) {
        Map<Range, List<Integer>> byRange = new LinkedHashMap<>();
        rows.forEach((z, xs) -> runs(xs).forEach(run ->
                byRange.computeIfAbsent(run, key -> new ArrayList<>()).add(z)));
        List<Area> areas = new ArrayList<>();
        byRange.forEach((range, zs) -> {
            zs.sort(Integer::compareTo);
            int from = zs.get(0);
            int last = from;
            for (int index = 1; index <= zs.size(); index++) {
                int z = index < zs.size() ? zs.get(index) : Integer.MIN_VALUE;
                if (z == last + 1) {
                    last = z;
                    continue;
                }
                areas.addAll(split(
                        new Area(layer.blockId(), range.x1(), layer.y(), from, range.x2(), layer.y(), last), limit));
                if (index < zs.size()) {
                    from = z;
                    last = z;
                }
            }
        });
        return areas;
    }

    private static List<Range> runs(Map<Integer, Integer> xs) {
        List<Range> found = new ArrayList<>();
        int start = Integer.MIN_VALUE;
        int last = Integer.MIN_VALUE;
        for (int x : new TreeMap<>(xs).keySet()) {
            if (start == Integer.MIN_VALUE) {
                start = x;
            } else if (x != last + 1) {
                found.add(new Range(start, last));
                start = x;
            }
            last = x;
        }
        if (start != Integer.MIN_VALUE) {
            found.add(new Range(start, last));
        }
        return found;
    }

    private static List<Area> split(Area area, int limit) {
        if (area.cells() <= limit) {
            return List.of(area);
        }
        int width = area.x2() - area.x1() + 1;
        int depth = area.z2() - area.z1() + 1;
        List<Area> parts = new ArrayList<>();
        if (width >= depth) {
            int step = Math.max(1, limit / depth);
            for (int x = area.x1(); x <= area.x2(); x += step) {
                parts.add(new Area(
                        area.blockId(),
                        x,
                        area.y1(),
                        area.z1(),
                        Math.min(x + step - 1, area.x2()),
                        area.y2(),
                        area.z2()));
            }
        } else {
            int step = Math.max(1, limit / width);
            for (int z = area.z1(); z <= area.z2(); z += step) {
                parts.add(new Area(
                        area.blockId(),
                        area.x1(),
                        area.y1(),
                        z,
                        area.x2(),
                        area.y2(),
                        Math.min(z + step - 1, area.z2())));
            }
        }
        return parts;
    }

    private record Layer(String blockId, int y) {}

    private record Range(int x1, int x2) {}
}
