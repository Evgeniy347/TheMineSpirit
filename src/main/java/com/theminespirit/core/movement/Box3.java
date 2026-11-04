package com.theminespirit.core.movement;

public record Box3(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    public Box3 {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException(
                    "Минимум коробки больше максимума: углы задаются упорядоченными, иначе пересечение бессмысленно");
        }
    }

    public boolean intersects(Box3 other) {
        return minX < other.maxX
                && maxX > other.minX
                && minY < other.maxY
                && maxY > other.minY
                && minZ < other.maxZ
                && maxZ > other.minZ;
    }
}
