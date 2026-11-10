package com.theminespirit.core.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class MapLegendPaint {
    public static final String KEY_CLOSE = "theminespirit.map.legend.close";
    public static final int TAB = 10;
    private static final int ICON_TOP = 2;
    private static final int STRIKE = 1;

    private MapLegendPaint() {}

    public static List<MapPaint.Shape> tab(MapViewport viewport) {
        int x = viewport.left() + viewport.width() - TAB - MapLegend.PAD;
        int y = viewport.top() + MapLegend.PAD;
        return List.of(
                new MapPaint.Fill(x, y, x + TAB, y + TAB, MapStyle.LEGEND_BORDER),
                new MapPaint.Fill(x + 1, y + 1, x + TAB - 1, y + TAB - 1, MapStyle.LEGEND_BACKGROUND),
                new MapPaint.Text(x + 3, y + 1, "?", MapStyle.TEXT));
    }

    public static boolean tabAt(MapViewport viewport, double mouseX, double mouseY) {
        int x = viewport.left() + viewport.width() - TAB - MapLegend.PAD;
        int y = viewport.top() + MapLegend.PAD;
        return mouseX >= x && mouseX < x + TAB && mouseY >= y && mouseY < y + TAB;
    }

    public static List<MapPaint.Shape> of(
            MapLegend legend, MapViewport viewport, MapPaint.Font font, MapLegend.Text text) {
        Objects.requireNonNull(legend, "Легенда не может быть null: пусто — MapLegend.initial()");
        MapLegend.Frame frame = legend.frame(viewport, font, text);
        if (!legend.open() || frame.width() <= 0 || frame.height() <= 0) {
            return List.of();
        }
        List<MapPaint.Shape> out = new ArrayList<>();
        out.add(new MapPaint.Fill(
                frame.x(), frame.y(), frame.x() + frame.width(), frame.y() + frame.height(), MapStyle.LEGEND_BORDER));
        out.add(new MapPaint.Fill(
                frame.x() + 1,
                frame.y() + 1,
                frame.x() + frame.width() - 1,
                frame.y() + frame.height() - 1,
                MapStyle.LEGEND_BACKGROUND));
        out.add(new MapPaint.Text(frame.closeX(), frame.closeY(), text.get(KEY_CLOSE), MapStyle.LEGEND_DIM));
        for (MapLegend.Frame.RowBox box : frame.rows()) {
            MapLegend.Row row = box.row();
            int color = row.dimmed() ? MapStyle.LEGEND_DIM : MapStyle.TEXT;
            int iconX = box.x1();
            int iconY = box.y1() + ICON_TOP;
            if (row.kind() == MapLegend.Kind.PATHS) {
                out.add(new MapPaint.Fill(iconX, iconY + 2, iconX + MapLegend.ICON, iconY + 4, MapStyle.PATH));
            } else {
                out.add(new MapPaint.Fill(iconX, iconY, iconX + MapLegend.ICON, iconY + MapLegend.ICON, row.color()));
            }
            int textX = iconX + MapLegend.ICON + MapLegend.GAP;
            out.add(new MapPaint.Text(textX, box.y1(), text.get(row.titleKey()), color));
            int width = font.width().applyAsInt(text.get(row.titleKey()));
            if (!row.noteKey().isEmpty()) {
                String note = row.showsPercent() ? text.get(row.noteKey(), row.percent()) : text.get(row.noteKey());
                out.add(new MapPaint.Text(
                        textX + width + MapLegend.GAP, box.y1(), note, row.crossed() ? MapStyle.LEGEND_DIM : color));
                width += MapLegend.GAP + font.width().applyAsInt(note);
            }
            if (row.crossed() || row.dimmed()) {
                out.add(new MapPaint.Fill(
                        iconX,
                        box.y1() + font.lineHeight() / 2,
                        textX + width,
                        box.y1() + font.lineHeight() / 2 + STRIKE,
                        MapStyle.LEGEND_DIM));
            }
        }
        return List.copyOf(out);
    }
}
