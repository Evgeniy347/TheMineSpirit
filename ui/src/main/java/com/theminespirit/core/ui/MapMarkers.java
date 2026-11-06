package com.theminespirit.core.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class MapMarkers {
    public static final int DOT = 5;
    private static final int GAP = 3;
    private static final int HIT = 2;
    private static final int LABEL_STEP = 1;

    private MapMarkers() {}

    public record Placed(MapMarker marker, int dotX, int dotY, int labelX, int labelY, int labelWidth) {
        public boolean dotAt(double screenX, double screenY) {
            return screenX >= dotX - HIT
                    && screenX < dotX + DOT + HIT
                    && screenY >= dotY - HIT
                    && screenY < dotY + DOT + HIT;
        }

        public boolean labelAt(double screenX, double screenY, int lineHeight) {
            return labelWidth > 0
                    && screenX >= labelX
                    && screenX < labelX + labelWidth
                    && screenY >= labelY
                    && screenY < labelY + lineHeight;
        }
    }

    public static List<Placed> layout(List<MapMarker> markers, MapViewport viewport, MapPaint.Font font) {
        Objects.requireNonNull(markers, "Метки карты не могут быть null: пусто — пустой список");
        Objects.requireNonNull(viewport, "Окно карты не может быть null");
        Objects.requireNonNull(font, "Шрифт меток не может быть null: по нему считается подпись");
        List<MapMarker> ordered = new ArrayList<>(markers);
        ordered.sort(Comparator.comparingDouble((MapMarker marker) -> viewport.screenY(marker.z() + 0.5))
                .thenComparingDouble(marker -> viewport.screenX(marker.x() + 0.5))
                .thenComparing(MapMarker::name));
        List<Placed> out = new ArrayList<>();
        List<Box> labels = new ArrayList<>();
        for (MapMarker marker : ordered) {
            int dotX = (int) Math.round(viewport.screenX(marker.x() + 0.5)) - DOT / 2;
            int dotY = (int) Math.round(viewport.screenY(marker.z() + 0.5)) - DOT / 2;
            if (!visible(dotX, dotY, viewport)) {
                continue;
            }
            dotX = clamp(dotX, viewport.left(), viewport.left() + viewport.width() - DOT);
            dotY = clamp(dotY, viewport.top(), viewport.top() + viewport.height() - DOT);
            int width = font.width().applyAsInt(marker.name());
            int labelX = labelX(dotX, width, viewport);
            int labelY = clamp(
                    dotY,
                    viewport.top(),
                    Math.max(viewport.top(), viewport.top() + viewport.height() - font.lineHeight()));
            labelY = freeY(labelX, labelY, width, labels, viewport, font.lineHeight());
            labels.add(new Box(labelX, labelY, labelX + width, labelY + font.lineHeight()));
            out.add(new Placed(marker, dotX, dotY, labelX, labelY, width));
        }
        return List.copyOf(out);
    }

    public static Optional<MapMarker> at(
            List<MapMarker> markers, MapViewport viewport, MapPaint.Font font, double screenX, double screenY) {
        if (!viewport.inside(screenX, screenY)) {
            return Optional.empty();
        }
        List<Placed> placed = layout(markers, viewport, font);
        Placed best = null;
        double distance = Double.MAX_VALUE;
        for (Placed marker : placed) {
            if (!marker.dotAt(screenX, screenY)) {
                continue;
            }
            double dx = screenX - (marker.dotX() + DOT / 2.0);
            double dy = screenY - (marker.dotY() + DOT / 2.0);
            double next = dx * dx + dy * dy;
            if (next < distance) {
                distance = next;
                best = marker;
            }
        }
        if (best != null) {
            return Optional.of(best.marker());
        }
        return placed.stream()
                .filter(marker -> marker.labelAt(screenX, screenY, font.lineHeight()))
                .map(Placed::marker)
                .findFirst();
    }

    public static List<MapPaint.Shape> paint(
            List<MapMarker> markers, MapViewport viewport, Optional<MapMarker> hovered, MapPaint.Font font) {
        Objects.requireNonNull(hovered, "Наведение метки не может быть null: пусто — Optional.empty()");
        List<Placed> placed = layout(markers, viewport, font);
        List<MapPaint.Shape> out = new ArrayList<>();
        for (Placed marker : placed) {
            boolean active = hovered.filter(marker.marker()::equals).isPresent();
            int border = active ? MapStyle.HOVER_BORDER : MapStyle.MARKER_BORDER;
            out.add(new MapPaint.Fill(
                    marker.dotX() - 1, marker.dotY() - 1, marker.dotX() + DOT + 1, marker.dotY() + DOT + 1, border));
            out.add(new MapPaint.Fill(
                    marker.dotX(),
                    marker.dotY(),
                    marker.dotX() + DOT,
                    marker.dotY() + DOT,
                    MapStyle.marker(marker.marker().kind())));
        }
        for (Placed marker : placed) {
            if (marker.labelWidth() <= 0) {
                continue;
            }
            out.add(new MapPaint.Fill(
                    marker.labelX() - 1,
                    marker.labelY() - 1,
                    marker.labelX() + marker.labelWidth() + 1,
                    marker.labelY() + font.lineHeight(),
                    MapStyle.LABEL_BACKGROUND));
            out.add(new MapPaint.Text(
                    marker.labelX(), marker.labelY(), marker.marker().name(), MapStyle.TEXT));
        }
        return out;
    }

    private static boolean visible(int dotX, int dotY, MapViewport viewport) {
        return dotX + DOT > viewport.left()
                && dotX < viewport.left() + viewport.width()
                && dotY + DOT > viewport.top()
                && dotY < viewport.top() + viewport.height();
    }

    private static int labelX(int dotX, int width, MapViewport viewport) {
        int right = viewport.left() + viewport.width();
        int after = dotX + DOT + GAP;
        if (after + width <= right) {
            return after;
        }
        int before = dotX - GAP - width;
        if (before >= viewport.left()) {
            return before;
        }
        return clamp(after, viewport.left(), Math.max(viewport.left(), right - width));
    }

    private static int freeY(int x, int y, int width, List<Box> labels, MapViewport viewport, int lineHeight) {
        int bottom = viewport.top() + viewport.height();
        int step = lineHeight + LABEL_STEP;
        int candidate = y;
        while (overlaps(x, candidate, width, labels, lineHeight) && candidate + step + lineHeight <= bottom) {
            candidate += step;
        }
        if (!overlaps(x, candidate, width, labels, lineHeight)) {
            return candidate;
        }
        candidate = y;
        while (overlaps(x, candidate, width, labels, lineHeight) && candidate - step >= viewport.top()) {
            candidate -= step;
        }
        return Math.max(viewport.top(), candidate);
    }

    private static boolean overlaps(int x, int y, int width, List<Box> labels, int lineHeight) {
        for (Box box : labels) {
            if (x < box.x2() && x + width > box.x1() && y < box.y2() && y + lineHeight > box.y1()) {
                return true;
            }
        }
        return false;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Box(int x1, int y1, int x2, int y2) {}
}
