package com.theminespirit.core.tactics.adapter;

import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.Orders;
import com.theminespirit.core.tactics.Point;
import java.util.Optional;

public final class OrdersBuilder {
    public record Zone(Point center, double radius) {}

    public record Ward(Point pos, boolean threatened) {}

    private OrdersBuilder() {}

    public static Orders build(CombatMode mode, Optional<Zone> zone, Optional<Ward> ward, boolean helpAvailable) {
        if (mode == null || zone == null || ward == null) {
            throw new IllegalArgumentException("OrdersBuilder.build: mode/zone/ward = null; отсутствие зоны или "
                    + "подопечного записывается Optional.empty(), режим боя обязателен");
        }
        Optional<Zone> usable = zone.filter(candidate -> candidate.radius() > 0);
        Point center = usable.map(Zone::center).orElse(null);
        double radius = usable.map(candidate -> Math.min(candidate.radius(), Orders.MAX_ZONE_RADIUS))
                .orElse(0.0);
        Point wardPos = ward.map(Ward::pos).orElse(null);
        boolean threatened = ward.map(Ward::threatened).orElse(false);
        return new Orders(mode, center, radius, wardPos, threatened, helpAvailable);
    }
}
