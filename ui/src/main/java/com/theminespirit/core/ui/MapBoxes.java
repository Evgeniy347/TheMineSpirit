package com.theminespirit.core.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class MapBoxes {
    private MapBoxes() {}

    public record Box(int x1, int y1, int x2, int y2) {}

    public static Optional<Box> of(TownMapView.MapRect rect, MapViewport viewport) {
        Objects.requireNonNull(rect, "Прямоугольник карты не может быть null");
        return clip(
                viewport,
                viewport.screenX(rect.x1()),
                viewport.screenY(rect.z1()),
                viewport.screenX(rect.x2() + 1.0),
                viewport.screenY(rect.z2() + 1.0));
    }

    public static List<Box> line(TownMapView.MapLine line, MapViewport viewport, int thickness) {
        Objects.requireNonNull(line, "Линия карты не может быть null");
        List<Box> boxes = new ArrayList<>();
        List<TownMapView.MapPoint> points = line.points();
        double half = thickness / 2.0;
        for (int index = 0; index < points.size(); index++) {
            TownMapView.MapPoint a = points.get(index);
            TownMapView.MapPoint b = points.get((index + 1) % points.size());
            double ax = viewport.screenX(a.x() + 0.5);
            double ay = viewport.screenY(a.z() + 0.5);
            double bx = viewport.screenX(b.x() + 0.5);
            double by = viewport.screenY(b.z() + 0.5);
            clip(
                            viewport,
                            Math.min(ax, bx) - half,
                            Math.min(ay, by) - half,
                            Math.max(ax, bx) + half,
                            Math.max(ay, by) + half)
                    .ifPresent(boxes::add);
        }
        return boxes;
    }

    private static Optional<Box> clip(MapViewport viewport, double x1, double y1, double x2, double y2) {
        int left = (int) Math.max(viewport.left(), Math.floor(x1));
        int top = (int) Math.max(viewport.top(), Math.floor(y1));
        int right = (int) Math.min(viewport.left() + viewport.width(), Math.ceil(x2));
        int bottom = (int) Math.min(viewport.top() + viewport.height(), Math.ceil(y2));
        return right > left && bottom > top ? Optional.of(new Box(left, top, right, bottom)) : Optional.empty();
    }
}
