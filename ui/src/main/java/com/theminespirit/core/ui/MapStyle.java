package com.theminespirit.core.ui;

import java.util.Objects;

public final class MapStyle {
    public static final int BACKGROUND = 0xFF2B2A26;
    public static final int LOT_BORDER = 0xFF1E1A14;
    public static final int HOVER_BORDER = 0xFFFFF3B0;
    public static final int CONTOUR = 0xFFE0D6C2;
    public static final int STAGE_FENCE = 0xFFC8862E;
    public static final int GATE = 0xFF4FC3F7;
    public static final int TEXT = 0xFFFFFFFF;
    public static final int MARKER_BORDER = 0xFF101010;
    public static final int LABEL_BACKGROUND = 0x99000000;
    public static final int SPIRIT = 0xFF66DD66;
    public static final int PLAYER = 0xFF54A0FF;
    public static final int ROUTE = 0xFFE0B050;
    private static final int PERCENT = 100;
    private static final int LOT_EMPTY = 0xA84E5A3A;
    private static final int LOT_DONE = 0xA86FA548;
    private static final int LOT_DAMAGED = 0xB0B0503A;

    private MapStyle() {}

    public static int fill(TownMapView.MapRect rect) {
        Objects.requireNonNull(rect, "Прямоугольник карты не может быть null");
        return switch (rect.kind()) {
            case STREET_MAIN -> 0xC08C8577;
            case STREET_SIDE -> 0xC077716A;
            case STREET_RING -> 0xC06B665F;
            case PATH -> 0xB8B79C6B;
            case LOT -> rect.damaged() ? LOT_DAMAGED : blend(LOT_EMPTY, LOT_DONE, rect.percent());
        };
    }

    public static int line(TownMapView.LineKind kind) {
        return kind == TownMapView.LineKind.CONTOUR ? CONTOUR : STAGE_FENCE;
    }

    public static int marker(MapMarker.Kind kind) {
        Objects.requireNonNull(kind, "Вид метки карты не может быть null");
        return switch (kind) {
            case SPIRIT -> SPIRIT;
            case PLAYER -> PLAYER;
            case ROUTE -> ROUTE;
        };
    }

    public static int blend(int from, int to, int percent) {
        int share = Math.max(0, Math.min(PERCENT, percent));
        int out = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int a = from >> shift & 0xFF;
            int b = to >> shift & 0xFF;
            out |= (a + (b - a) * share / PERCENT) << shift;
        }
        return out;
    }
}
