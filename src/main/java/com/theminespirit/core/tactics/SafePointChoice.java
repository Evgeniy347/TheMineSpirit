package com.theminespirit.core.tactics;

import java.util.Objects;
import java.util.Optional;

public final class SafePointChoice {
    public static final Optional<Point> NO_CAMP = Optional.empty();

    private SafePointChoice() {
    }

    public static Optional<Point> nearest(Point self, Optional<Point> home, Optional<Point> camp) {
        Objects.requireNonNull(self, "self не может быть null: расстояние до точки меряется от духа");
        Objects.requireNonNull(home, "home не может быть null: отсутствие дома — Optional.empty()");
        Objects.requireNonNull(camp, "camp не может быть null: отсутствие лагеря — Optional.empty()");
        if (home.isEmpty()) {
            return camp;
        }
        if (camp.isEmpty()) {
            return home;
        }
        Point homePoint = home.get();
        Point campPoint = camp.get();
        return self.distanceTo(campPoint) < self.distanceTo(homePoint) ? camp : home;
    }
}
