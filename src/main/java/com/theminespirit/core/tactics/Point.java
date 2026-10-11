package com.theminespirit.core.tactics;

public record Point(double x, double y, double z) {
    public Point {
        FactChecks.range("Point.x", x, -3.0E7, 3.0E7);
        FactChecks.range("Point.y", y, -4096, 4096);
        FactChecks.range("Point.z", z, -3.0E7, 3.0E7);
    }

    public double distanceTo(Point other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
