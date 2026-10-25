package com.theminespirit.core.ui;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

public final class MapHit {
    private MapHit() {}

    public static Optional<TownMapView.MapRect> lotAt(
            TownMapView view, MapViewport viewport, double screenX, double screenY) {
        Objects.requireNonNull(view, "Карта не может быть null");
        Objects.requireNonNull(viewport, "Окно карты не может быть null");
        if (!viewport.inside(screenX, screenY)) {
            return Optional.empty();
        }
        double x = viewport.worldX(screenX);
        double z = viewport.worldZ(screenY);
        return view.rects().stream()
                .filter(rect -> rect.kind() == TownMapView.Kind.LOT && rect.covers(x, z))
                .min(Comparator.comparingLong(TownMapView.MapRect::area));
    }
}
