package com.theminespirit.core.ui;

import java.util.List;
import java.util.Objects;

public record MapHint(String title, String key, List<Integer> args) {
    public static final String KEY_LOT = "theminespirit.map.lot";
    public static final String KEY_DAMAGED = "theminespirit.map.lot.damaged";
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
}
