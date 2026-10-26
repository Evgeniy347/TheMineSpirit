package com.theminespirit.core.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.ToIntFunction;

public final class MapPaint {
    public static final int LINE = 2;
    public static final int LABEL_PAD = 2;
    private static final int GATE_MIN = 4;

    private MapPaint() {}

    public sealed interface Shape permits Fill, Text, Raster {}

    public record Fill(int x1, int y1, int x2, int y2, int color) implements Shape {}

    public record Text(int x, int y, String text, int color) implements Shape {}

    public record Raster(int x1, int y1, int x2, int y2, TerrainRaster raster) implements Shape {
        public Raster {
            Objects.requireNonNull(raster, "Растр местности в слое карты не может быть null");
        }
    }

    public record Font(ToIntFunction<String> width, int lineHeight) {
        public Font {
            Objects.requireNonNull(width, "Ширина текста не может быть null: по ней решается, влезет ли подпись");
        }
    }

    public static List<Shape> of(
            TownMapView view, MapViewport viewport, Optional<TownMapView.MapRect> hovered, Font font) {
        return of(view, Optional.empty(), viewport, hovered, font);
    }

    public static List<Shape> of(
            TownMapView view,
            Optional<TerrainRaster> terrain,
            MapViewport viewport,
            Optional<TownMapView.MapRect> hovered,
            Font font) {
        Objects.requireNonNull(view, "Карта не может быть null");
        Objects.requireNonNull(terrain, "Слой местности не может быть null: пусто — Optional.empty()");
        Objects.requireNonNull(viewport, "Окно карты не может быть null");
        List<Shape> out = new ArrayList<>();
        out.add(new Fill(
                viewport.left(),
                viewport.top(),
                viewport.left() + viewport.width(),
                viewport.top() + viewport.height(),
                MapStyle.BACKGROUND));
        terrain.filter(raster -> !raster.isEmpty()).ifPresent(raster -> out.add(terrain(raster, viewport)));
        List<Text> labels = new ArrayList<>();
        for (TownMapView.MapRect rect : view.rects()) {
            MapBoxes.of(rect, viewport)
                    .ifPresent(box -> rect(
                            out, labels, rect, box, hovered.filter(rect::equals).isPresent(), font));
        }
        for (TownMapView.MapLine line : view.lines()) {
            int color = MapStyle.line(line.kind());
            MapBoxes.line(line, viewport, LINE).forEach(box -> out.add(fill(box, color)));
        }
        for (TownMapView.MapLine line : view.lines()) {
            gates(out, line, viewport);
        }
        out.addAll(labels);
        return out;
    }

    private static void rect(
            List<Shape> out,
            List<Text> labels,
            TownMapView.MapRect rect,
            MapBoxes.Box box,
            boolean hovered,
            Font font) {
        if (rect.kind() != TownMapView.Kind.LOT) {
            out.add(fill(box, MapStyle.fill(rect)));
            return;
        }
        out.add(fill(box, hovered ? MapStyle.HOVER_BORDER : MapStyle.LOT_BORDER));
        if (box.x2() - box.x1() > 2 && box.y2() - box.y1() > 2) {
            out.add(new Fill(box.x1() + 1, box.y1() + 1, box.x2() - 1, box.y2() - 1, MapStyle.fill(rect)));
        }
        boolean wide = font.width().applyAsInt(rect.label()) + 2 * LABEL_PAD <= box.x2() - box.x1();
        boolean tall = font.lineHeight() + 2 * LABEL_PAD <= box.y2() - box.y1();
        if (wide && tall && !rect.label().isEmpty()) {
            labels.add(new Text(box.x1() + LABEL_PAD, box.y1() + LABEL_PAD, rect.label(), MapStyle.TEXT));
        }
    }

    private static Raster terrain(TerrainRaster raster, MapViewport viewport) {
        int x1 = (int) Math.round(viewport.screenX(raster.x1()));
        int y1 = (int) Math.round(viewport.screenY(raster.z1()));
        int x2 = (int) Math.round(viewport.screenX(raster.x1() + raster.width()));
        int y2 = (int) Math.round(viewport.screenY(raster.z1() + raster.height()));
        return new Raster(x1, y1, x2, y2, raster);
    }

    private static void gates(List<Shape> out, TownMapView.MapLine line, MapViewport viewport) {
        int side = (int) Math.max(GATE_MIN, Math.ceil(viewport.scale()));
        for (TownMapView.MapPoint gate : line.gates()) {
            int x = (int) Math.round(viewport.screenX(gate.x() + 0.5)) - side / 2;
            int y = (int) Math.round(viewport.screenY(gate.z() + 0.5)) - side / 2;
            int x1 = Math.max(viewport.left(), x);
            int y1 = Math.max(viewport.top(), y);
            int x2 = Math.min(viewport.left() + viewport.width(), x + side);
            int y2 = Math.min(viewport.top() + viewport.height(), y + side);
            if (x2 > x1 && y2 > y1) {
                out.add(new Fill(x1, y1, x2, y2, MapStyle.GATE));
            }
        }
    }

    private static Fill fill(MapBoxes.Box box, int color) {
        return new Fill(box.x1(), box.y1(), box.x2(), box.y2(), color);
    }
}
