package com.theminespirit.core.tactics;

public record Orders(
        CombatMode mode,
        Point zoneCenter,
        double zoneRadius,
        Point ward,
        boolean wardThreatened,
        boolean helpAvailable) {
    public static final double MAX_ZONE_RADIUS = 256;

    public Orders {
        FactChecks.required("Orders.mode", mode);
        FactChecks.range("Orders.zoneRadius", zoneRadius, 0, MAX_ZONE_RADIUS);
        if ((zoneCenter == null) != (zoneRadius == 0)) {
            throw new IllegalArgumentException("Orders.zoneCenter = " + zoneCenter + ", zoneRadius = " + zoneRadius
                    + ": зона задана, только если есть и центр, и радиус больше 0; проверьте оба поля вместе");
        }
        if (wardThreatened && ward == null) {
            throw new IllegalArgumentException("Orders.wardThreatened = true без подопечного (ward = null): "
                    + "угрожать можно только подопечному, которого видно");
        }
    }

    public static Orders mode(CombatMode mode) {
        return new Orders(mode, null, 0, null, false, false);
    }

    public boolean zoneSet() {
        return zoneCenter != null;
    }

    public boolean wardPresent() {
        return ward != null;
    }
}
