package com.theminespirit.core.ui;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.TownSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

public record TownMapView(List<MapRect> rects, List<MapLine> lines, List<MapMarker> markers) {
    public enum Kind {
        STREET_MAIN,
        STREET_SIDE,
        STREET_RING,
        PATH,
        LOT
    }

    public enum LineKind {
        CONTOUR,
        STAGE_FENCE
    }

    public record MapRect(Kind kind, int x1, int z1, int x2, int z2, String label, int percent, boolean damaged) {
        public MapRect {
            Objects.requireNonNull(kind, "Вид прямоугольника карты не может быть null");
            Objects.requireNonNull(label, "Подпись не может быть null: у улиц и тропинок она пустая");
            if (x2 < x1 || z2 < z1 || percent < 0 || percent > 100) {
                throw new IllegalArgumentException("Прямоугольник карты " + x1 + "," + z1 + "–" + x2 + "," + z2
                        + " с готовностью " + percent + "%: углы по возрастанию, готовность 0..100");
            }
        }

        public boolean covers(double x, double z) {
            return x >= x1 && x < x2 + 1 && z >= z1 && z < z2 + 1;
        }

        public long area() {
            return (long) (x2 - x1 + 1) * (z2 - z1 + 1);
        }
    }

    public record MapPoint(int x, int z) {}

    public record MapLine(LineKind kind, List<MapPoint> points, List<MapPoint> gates) {
        public MapLine {
            Objects.requireNonNull(kind, "Вид линии карты не может быть null");
            points = List.copyOf(points);
            gates = List.copyOf(gates);
        }
    }

    public record Bounds(int x1, int z1, int x2, int z2) {
        public Bounds union(Bounds other) {
            return new Bounds(
                    Math.min(x1, other.x1), Math.min(z1, other.z1), Math.max(x2, other.x2), Math.max(z2, other.z2));
        }
    }

    public TownMapView {
        rects = List.copyOf(Objects.requireNonNull(rects, "Прямоугольники карты не могут быть null"));
        lines = List.copyOf(Objects.requireNonNull(lines, "Линии карты не могут быть null"));
        markers = List.copyOf(Objects.requireNonNull(markers, "Метки карты не могут быть null"));
    }

    public TownMapView(List<MapRect> rects, List<MapLine> lines) {
        this(rects, lines, List.of());
    }

    public TownMapView withMarkers(List<MapMarker> next) {
        return new TownMapView(rects, lines, next);
    }

    public static TownMapView of(TownSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "Снимок плана не может быть null: карта строится по нему");
        List<MapRect> rects = new ArrayList<>();
        for (TownSnapshot.Street street : snapshot.streets()) {
            rects.add(new MapRect(
                    streetKind(street.kind()), street.x1(), street.z1(), street.x2(), street.z2(), "", 0, false));
        }
        rects.addAll(paths(snapshot.paths()));
        for (TownSnapshot.Lot lot : snapshot.lots()) {
            rects.add(new MapRect(
                    Kind.LOT,
                    lot.x(),
                    lot.z(),
                    lot.x() + lot.width() - 1,
                    lot.z() + lot.depth() - 1,
                    lot.name(),
                    lot.progress().percent(),
                    lot.progress().damaged()));
        }
        List<MapLine> lines = List.of(
                new MapLine(LineKind.CONTOUR, points(snapshot.contour()), points(snapshot.contourGates())),
                new MapLine(LineKind.STAGE_FENCE, points(snapshot.stageFence()), points(snapshot.stageGates())));
        return new TownMapView(rects, lines);
    }

    public Bounds bounds() {
        int x1 = Integer.MAX_VALUE;
        int z1 = Integer.MAX_VALUE;
        int x2 = Integer.MIN_VALUE;
        int z2 = Integer.MIN_VALUE;
        for (MapRect rect : rects) {
            x1 = Math.min(x1, rect.x1());
            z1 = Math.min(z1, rect.z1());
            x2 = Math.max(x2, rect.x2());
            z2 = Math.max(z2, rect.z2());
        }
        for (MapLine line : lines) {
            for (MapPoint point : line.points()) {
                x1 = Math.min(x1, point.x());
                z1 = Math.min(z1, point.z());
                x2 = Math.max(x2, point.x());
                z2 = Math.max(z2, point.z());
            }
        }
        return x1 > x2 ? new Bounds(0, 0, 0, 0) : new Bounds(x1, z1, x2, z2);
    }

    private static Kind streetKind(String kind) {
        return switch (kind) {
            case "main" -> Kind.STREET_MAIN;
            case "side" -> Kind.STREET_SIDE;
            default -> Kind.STREET_RING;
        };
    }

    private static List<MapPoint> points(List<ColumnMask.Cell> cells) {
        return cells.stream().map(cell -> new MapPoint(cell.x(), cell.z())).toList();
    }

    private static List<MapRect> paths(List<List<BlockCoord>> segments) {
        Map<Integer, NavigableSet<Integer>> rows = new TreeMap<>();
        for (List<BlockCoord> segment : segments) {
            for (BlockCoord cell : segment) {
                rows.computeIfAbsent(cell.z(), z -> new TreeSet<>()).add(cell.x());
            }
        }
        List<MapRect> out = new ArrayList<>();
        rows.forEach((z, xs) -> {
            int start = xs.first();
            int last = start;
            for (int x : xs.tailSet(start, false)) {
                if (x != last + 1) {
                    out.add(new MapRect(Kind.PATH, start, z, last, z, "", 0, false));
                    start = x;
                }
                last = x;
            }
            out.add(new MapRect(Kind.PATH, start, z, last, z, "", 0, false));
        });
        return out;
    }
}
