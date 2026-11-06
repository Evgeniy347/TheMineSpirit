package com.theminespirit.core.town;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class YardStations {
    private YardStations() {}

    public static BlockCoord nearest(List<BlockCoord> stations, BlockCoord from, BlockCoord fallback) {
        Objects.requireNonNull(stations, "Станки не могут быть null: пустой список — нет станка");
        Objects.requireNonNull(from, "Точка отсчёта не может быть null: по ней выбирается ближайший станок");
        Objects.requireNonNull(fallback, "Запасная точка не может быть null: без станка цель всё равно нужна");
        return stations.stream()
                .min(Comparator.comparingLong((BlockCoord at) -> distance(from, at))
                        .thenComparingInt(BlockCoord::y)
                        .thenComparingInt(BlockCoord::x))
                .orElse(fallback);
    }

    private static long distance(BlockCoord from, BlockCoord to) {
        long dx = (long) to.x() - from.x();
        long dy = (long) to.y() - from.y();
        long dz = (long) to.z() - from.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
