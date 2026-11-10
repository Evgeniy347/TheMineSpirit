package com.theminespirit.core.ui;

import java.util.List;
import java.util.Objects;

public record MapHint(String title, String key, List<Integer> args) {
    public static final String KEY_LOT = "theminespirit.map.lot";
    public static final String KEY_DAMAGED = "theminespirit.map.lot.damaged";
    public static final String KEY_PLAYER = "theminespirit.map.player";
    public static final String KEY_NO_JOB = "theminespirit.map.no_job";
    public static final String KEY_ROUTE = "theminespirit.map.route";
    public static final String KEY_QUARRY = "theminespirit.map.quarry";
    private static final int PERCENT = 100;

    public MapHint {
        Objects.requireNonNull(title, "Название постройки в подсказке не может быть null");
        Objects.requireNonNull(key, "Ключ перевода подсказки не может быть null");
        args = List.copyOf(args);
    }

    public static MapHint of(TownMapView.MapRect lot) {
        Objects.requireNonNull(lot, "Участок подсказки не может быть null");
        if (lot.kind() != TownMapView.Kind.LOT) {
            throw new IllegalArgumentException(
                    "Подсказка карты — только для участков построек, а не для " + lot.kind());
        }
        return lot.damaged()
                ? new MapHint(lot.label(), KEY_DAMAGED, List.of(lot.percent(), PERCENT - lot.percent()))
                : new MapHint(lot.label(), KEY_LOT, List.of(lot.percent()));
    }

    public static MapHint of(MapMarker marker) {
        Objects.requireNonNull(marker, "Метка подсказки не может быть null");
        if (marker.kind() == MapMarker.Kind.PLAYER) {
            return new MapHint(marker.name(), KEY_PLAYER, List.of());
        }
        if (marker.kind() == MapMarker.Kind.ROUTE) {
            return new MapHint(marker.name(), KEY_ROUTE, List.of());
        }
        if (marker.kind() == MapMarker.Kind.DIRT || marker.kind() == MapMarker.Kind.SAND) {
            return new MapHint(marker.name(), KEY_QUARRY, List.of());
        }
        String key = marker.occupation().isEmpty() ? KEY_NO_JOB : marker.occupation();
        return new MapHint(marker.name(), key, List.of());
    }
}
