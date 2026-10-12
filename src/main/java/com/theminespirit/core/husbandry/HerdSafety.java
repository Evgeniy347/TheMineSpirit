package com.theminespirit.core.husbandry;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class HerdSafety {
    public static final double MOTHER_WATCH_RADIUS = 2.0;

    private HerdSafety() {
    }

    public static Optional<String> dangerReason(boolean inLava, boolean onFire, boolean inWater) {
        if (inLava) {
            return Optional.of("в лаве");
        }
        if (onFire) {
            return Optional.of("в огне");
        }
        if (inWater) {
            return Optional.of("в воде");
        }
        return Optional.empty();
    }

    public static boolean watchedByBaby(AnimalRef target, List<AnimalRef> herd) {
        Objects.requireNonNull(target, "Цель не может быть null: неизвестно, рядом с кем ищут детёныша");
        Objects.requireNonNull(herd, "Снимок стада не может быть null: пустое стадо задают пустым списком");
        for (AnimalRef animal : herd) {
            if (animal.baby() && animal.species() == target.species() && !animal.id().equals(target.id())
                    && distanceSquared(target.pos(), animal.pos()) <= MOTHER_WATCH_RADIUS * MOTHER_WATCH_RADIUS) {
                return true;
            }
        }
        return false;
    }

    private static double distanceSquared(BlockCoord from, BlockCoord to) {
        long dx = (long) to.x() - from.x();
        long dy = (long) to.y() - from.y();
        long dz = (long) to.z() - from.z();
        return (double) dx * dx + dy * dy + dz * dz;
    }
}
