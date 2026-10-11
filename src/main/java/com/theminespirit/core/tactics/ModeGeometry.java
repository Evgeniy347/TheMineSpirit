package com.theminespirit.core.tactics;

import java.util.Optional;

public record ModeGeometry(Point anchor, double zoneDistance, double leashLeft, double engageLeft) {
    public static ModeGeometry of(Situation situation, ModeParams params, Optional<Point> memoryAnchor) {
        Orders orders = situation.orders();
        Point self = situation.self().pos();
        Point anchor = anchorOf(params, orders, memoryAnchor, self);
        double zoneRadius = orders.zoneRadius();
        double leashLeft = Assessment.UNBOUNDED;
        double engageLeft = Assessment.UNBOUNDED;
        boolean first = true;
        for (EnemyFact enemy : situation.enemies()) {
            double left = Leash.check(params, zoneRadius, anchor, self, enemy.pos()).left();
            double engage = params.engage(zoneRadius) - anchor.distanceTo(enemy.pos());
            leashLeft = first ? left : Math.max(leashLeft, left);
            engageLeft = first ? engage : Math.max(engageLeft, engage);
            first = false;
        }
        double zoneDistance = orders.zoneSet() ? self.distanceTo(orders.zoneCenter()) : 0;
        return new ModeGeometry(anchor, zoneDistance, leashLeft, engageLeft);
    }

    private static Point anchorOf(ModeParams params, Orders orders, Optional<Point> memoryAnchor, Point self) {
        return switch (params.anchor()) {
            case SELF -> memoryAnchor.orElse(self);
            case ZONE -> orders.zoneSet() ? orders.zoneCenter() : memoryAnchor.orElse(self);
            case PROTECTED -> orders.wardPresent() ? orders.ward() : memoryAnchor.orElse(self);
        };
    }
}
