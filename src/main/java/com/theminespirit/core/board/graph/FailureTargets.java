package com.theminespirit.core.board.graph;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class FailureTargets {
    private FailureTargets() {}

    public static String key(BlockCoord at) {
        Objects.requireNonNull(at, "Цель не может быть null: без неё нечего исключать");
        return at.x() + "," + at.y() + "," + at.z();
    }

    public static BlockCoord choose(List<BlockCoord> candidates, Set<String> unreachable, double radius) {
        Objects.requireNonNull(candidates, "Список целей не может быть null: без целей задание не ставится");
        Objects.requireNonNull(unreachable, "Недостижимые цели не могут быть null: пустые задают пустым набором");
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("Нет ни одной цели: узел без цели не выбирает место работы");
        }
        if (!Double.isFinite(radius) || radius < 0) {
            throw new IllegalArgumentException(
                    "Радиус недоступной цели " + radius + ": он неотрицателен, ноль — «только точная цель»");
        }
        for (BlockCoord candidate : candidates) {
            if (!near(candidate, unreachable, radius)) {
                return candidate;
            }
        }
        return candidates.get(0);
    }

    public static boolean near(BlockCoord at, Set<String> unreachable, double radius) {
        double limit = radius * radius;
        for (String known : unreachable) {
            if (near(at, known, limit)) {
                return true;
            }
        }
        return false;
    }

    private static boolean near(BlockCoord at, String key, double limit) {
        int first = key.indexOf(',');
        int second = first < 0 ? -1 : key.indexOf(',', first + 1);
        if (second < 0 || key.indexOf(',', second + 1) >= 0) {
            return false;
        }
        try {
            int dx = at.x() - Integer.parseInt(key.substring(0, first).trim());
            int dy = at.y() - Integer.parseInt(key.substring(first + 1, second).trim());
            int dz = at.z() - Integer.parseInt(key.substring(second + 1).trim());
            return (double) dx * dx + (double) dy * dy + (double) dz * dz <= limit;
        } catch (NumberFormatException invalid) {
            return false;
        }
    }
}
