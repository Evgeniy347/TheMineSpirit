package com.theminespirit.core.base;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public final class RespawnCell {
    public static final int SEARCH_RADIUS = 8;

    private RespawnCell() {}

    public record Terrain(Predicate<BlockCoord> floor, Predicate<BlockCoord> air) {
        public Terrain {
            Objects.requireNonNull(floor, "Пол клетки возрождения не может быть null: без него дух провалится");
            Objects.requireNonNull(air, "Свободная клетка не может быть null: без неё дух застрянет в блоке");
        }
    }

    public static Optional<BlockCoord> base(Optional<BlockCoord> plotAnchor, Optional<BlockCoord> home) {
        Objects.requireNonNull(plotAnchor, "Якорь участка задаётся Optional.empty(), а не null");
        Objects.requireNonNull(home, "Дом духа задаётся Optional.empty(), а не null");
        return plotAnchor.isPresent() ? plotAnchor : home;
    }

    public static Optional<BlockCoord> nearest(BlockCoord base, Terrain terrain) {
        return nearest(base, SEARCH_RADIUS, terrain);
    }

    public static Optional<BlockCoord> nearest(BlockCoord base, int radius, Terrain terrain) {
        Objects.requireNonNull(base, "Точка базы не может быть null: от неё ищется клетка возрождения");
        Objects.requireNonNull(terrain, "Мир клетки возрождения не может быть null: без него клетку не выбрать");
        if (radius < 0) {
            throw new IllegalArgumentException(
                    "Радиус поиска " + radius + " меньше нуля: ближайшую клетку ищут в непустой окрестности базы");
        }
        BlockCoord best = null;
        long bestDistance = Long.MAX_VALUE;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockCoord feet = new BlockCoord(base.x() + dx, base.y() + dy, base.z() + dz);
                    if (!free(feet, terrain)) {
                        continue;
                    }
                    long distance = distance(base, feet);
                    if (distance == 0L) {
                        return Optional.of(feet);
                    }
                    if (best == null) {
                        best = feet;
                        bestDistance = distance;
                        continue;
                    }
                    boolean closer = distance < bestDistance;
                    boolean tieBreak = distance == bestDistance && feet.compareTo(best) < 0;
                    if (closer || tieBreak) {
                        best = feet;
                        bestDistance = distance;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean free(BlockCoord feet, Terrain terrain) {
        BlockCoord floor = new BlockCoord(feet.x(), feet.y() - 1, feet.z());
        BlockCoord above = new BlockCoord(feet.x(), feet.y() + 1, feet.z());
        return terrain.floor().test(floor)
                && terrain.air().test(feet)
                && terrain.air().test(above);
    }

    private static long distance(BlockCoord first, BlockCoord second) {
        long dx = (long) first.x() - second.x();
        long dy = (long) first.y() - second.y();
        long dz = (long) first.z() - second.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
