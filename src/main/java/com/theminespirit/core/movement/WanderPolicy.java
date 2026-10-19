package com.theminespirit.core.movement;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;
import java.util.SplittableRandom;

public final class WanderPolicy {
    public static final int VERTICAL_SPREAD = 2;

    private static final long ATTEMPT_MIX = 0x9E3779B97F4A7C15L;

    private WanderPolicy() {}

    public static BlockCoord next(long seed, BlockCoord origin, int radius, int attempt) {
        Objects.requireNonNull(origin, "Старт блуждания не может быть null: без него неизвестно, вокруг чего бродить");
        if (radius < 0) {
            throw new IllegalArgumentException("Радиус блуждания " + radius
                    + " отрицателен: точка с отрицательным радиусом недостижима и дух встанет");
        }
        if (attempt < 0) {
            throw new IllegalArgumentException(
                    "Попытка " + attempt + " отрицательна: счётчик попыток растёт от нуля при каждом застревании");
        }
        if (radius == 0) {
            return origin;
        }
        SplittableRandom random = new SplittableRandom(seed ^ (attempt * ATTEMPT_MIX));
        int dx = random.nextInt(-radius, radius + 1);
        int dz = random.nextInt(-radius, radius + 1);
        int dy = random.nextInt(-VERTICAL_SPREAD, VERTICAL_SPREAD + 1);
        return new BlockCoord(origin.x() + dx, origin.y() + dy, origin.z() + dz);
    }
}
