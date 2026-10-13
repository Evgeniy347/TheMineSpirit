package com.theminespirit.core.base;

import com.theminespirit.core.tactics.Point;
import java.util.Optional;

public final class CampSpot {
    private CampSpot() {
    }

    public static Optional<Point> choose(Point self, Optional<Point> objective, Optional<Point> home,
            double standoff) {
        if (self == null) {
            throw new IllegalArgumentException("Постановка лагеря без духа не считается: лагерь ставят со стороны духа");
        }
        if (objective == null) {
            throw new IllegalArgumentException(
                    "Постановка лагеря без контейнера цели не считается: нет цели — Optional.empty()");
        }
        if (home == null) {
            throw new IllegalArgumentException(
                    "Постановка лагеря без контейнера дома не считается: нет дома — Optional.empty()");
        }
        if (!Double.isFinite(standoff) || standoff < CampTuning.CAMP_MIN_OBJECTIVE_DISTANCE) {
            throw new IllegalArgumentException("Заходная дистанция = " + standoff
                    + ": обязана быть конечной и не меньше минимума до цели "
                    + CampTuning.CAMP_MIN_OBJECTIVE_DISTANCE + ", иначе лагерь встанет на саму цель");
        }
        if (objective.isEmpty()) {
            return Optional.empty();
        }
        Point target = objective.get();
        double dx = self.x() - target.x();
        double dy = self.y() - target.y();
        double dz = self.z() - target.z();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 1e-6) {
            return Optional.empty();
        }
        double reach = Math.min(Math.max(distance, CampTuning.CAMP_MIN_OBJECTIVE_DISTANCE), standoff);
        Point camp = new Point(target.x() + dx / distance * reach, target.y() + dy / distance * reach,
                target.z() + dz / distance * reach);
        if (home.isPresent() && home.get().distanceTo(camp) < standoff) {
            Optional<Point> pushed = pushFromHome(camp, home.get(), standoff);
            if (pushed.isEmpty() || pushed.get().distanceTo(target) < CampTuning.CAMP_MIN_OBJECTIVE_DISTANCE) {
                return Optional.empty();
            }
            camp = pushed.get();
        }
        return Optional.of(camp);
    }

    private static Optional<Point> pushFromHome(Point camp, Point home, double standoff) {
        double dx = camp.x() - home.x();
        double dy = camp.y() - home.y();
        double dz = camp.z() - home.z();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 1e-6) {
            return Optional.empty();
        }
        return Optional.of(new Point(home.x() + dx / distance * standoff, home.y() + dy / distance * standoff,
                home.z() + dz / distance * standoff));
    }
}
