package com.theminespirit.core.tactics;

import java.util.Optional;

public final class SafePointChoice {
    private SafePointChoice() {
    }

    public static Optional<Point> nearest(Point self, Optional<Point> home, Optional<Point> camp) {
        if (self == null) {
            throw new IllegalArgumentException("Выбор точки отхода без духа не считается: ближе меряют от духа");
        }
        if (home == null) {
            throw new IllegalArgumentException(
                    "Выбор точки отхода без контейнера дома не считается: нет дома — Optional.empty()");
        }
        if (camp == null) {
            throw new IllegalArgumentException(
                    "Выбор точки отхода без контейнера лагеря не считается: нет лагеря — Optional.empty()");
        }
        if (home.isEmpty()) {
            return camp;
        }
        if (camp.isEmpty()) {
            return home;
        }
        double homeDistance = self.distanceTo(home.get());
        double campDistance = self.distanceTo(camp.get());
        if (campDistance < homeDistance) {
            return camp;
        }
        return home;
    }
}
