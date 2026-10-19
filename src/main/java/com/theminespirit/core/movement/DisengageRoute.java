package com.theminespirit.core.movement;

import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.adapter.RetreatPlanner;
import java.util.Objects;

public final class DisengageRoute {
    public static final double ARRIVAL_SLACK = 0.5;
    public static final double MIN_STEP = 1.0;

    private DisengageRoute() {}

    public static Point away(Point self, Point threat, int distance) {
        Objects.requireNonNull(self, "self не может быть null: точка отхода считается от позиции духа");
        Objects.requireNonNull(threat, "threat не может быть null: без угрозы не от чего отходить");
        if (distance < CatalogLimits.DISENGAGE_DISTANCE_MIN) {
            throw new IllegalArgumentException("Дистанция выхода из боя " + distance + " меньше "
                    + CatalogLimits.DISENGAGE_DISTANCE_MIN + ": отходить на ноль блоков значит остаться в бою");
        }
        double dx = self.x() - threat.x();
        double dz = self.z() - threat.z();
        double length = Math.hypot(dx, dz);
        if (length < 1.0E-6) {
            dx = 1.0;
            dz = 0.0;
            length = 1.0;
        }
        double target = Math.max(distance + RetreatPlanner.HOME_ARRIVAL + ARRIVAL_SLACK, length + MIN_STEP);
        return new Point(threat.x() + dx / length * target, self.y(), threat.z() + dz / length * target);
    }

    public static boolean reached(Point self, Point threat, int distance) {
        Objects.requireNonNull(self, "self не может быть null: без позиции духа расстояние не измерить");
        Objects.requireNonNull(threat, "threat не может быть null: без угрозы расстояние не измерить");
        return horizontal(self, threat) >= distance;
    }

    private static double horizontal(Point self, Point threat) {
        double dx = self.x() - threat.x();
        double dz = self.z() - threat.z();
        return Math.hypot(dx, dz);
    }
}
