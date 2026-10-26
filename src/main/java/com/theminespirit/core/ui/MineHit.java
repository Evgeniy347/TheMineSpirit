package com.theminespirit.core.ui;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

public final class MineHit {
    private MineHit() {}

    public static Optional<MineSnapshot.Rect> rectAt(
            MineSnapshot snapshot, MapViewport viewport, double screenX, double screenY) {
        Objects.requireNonNull(snapshot, "Срез шахты не может быть null");
        Objects.requireNonNull(viewport, "Окно карты не может быть null");
        if (!viewport.inside(screenX, screenY)) {
            return Optional.empty();
        }
        double x = viewport.worldX(screenX);
        double z = viewport.worldZ(screenY);
        return snapshot.rects().stream()
                .filter(rect -> rect.covers(x, z))
                .min(Comparator.comparingLong(MineSnapshot.Rect::area));
    }
}
