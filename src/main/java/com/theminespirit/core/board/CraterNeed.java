package com.theminespirit.core.board;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class CraterNeed {
    private static final String DIRT = "dirt";

    private CraterNeed() {}

    public static Map<String, Integer> estimate(Point blast, double radius) {
        Objects.requireNonNull(blast, "blast не может быть null: точка взрыва — центр воронки");
        if (radius < 0) {
            throw new IllegalArgumentException(
                    "Радиус взрыва равен " + radius + " и отрицателен: взрыв не имеет отрицательного радиуса");
        }
        if (radius == 0) {
            return Map.of();
        }
        int blocks = (int) Math.ceil(Math.PI * radius * radius);
        return Map.of(DIRT, blocks);
    }

    public static boolean outsideTerritory(Point blast, List<Point> homes, double radius) {
        Objects.requireNonNull(blast, "blast не может быть null: точка взрыва — центр воронки");
        Objects.requireNonNull(homes, "homes не может быть null: список домов задают пустым списком");
        if (radius < 0) {
            throw new IllegalArgumentException(
                    "Радиус территории равен " + radius + " и отрицателен: территория не имеет отрицательного радиуса");
        }
        return homes.stream().allMatch(home -> distance(blast, home) > radius);
    }

    private static double distance(Point first, Point second) {
        double dx = first.x() - second.x();
        double dz = first.z() - second.z();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
