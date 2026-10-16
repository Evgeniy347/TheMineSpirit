package com.theminespirit.core.sleep;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class BedChoice {
    private BedChoice() {
    }

    public static Optional<BlockCoord> choose(Optional<BlockCoord> ownBed, Collection<BlockCoord> beds,
            Set<BlockCoord> taken, BlockCoord from, BlockCoord home, int settlementRadius) {
        Objects.requireNonNull(ownBed, "Своя кровать не может быть null: отсутствие задают Optional.empty()");
        Objects.requireNonNull(beds, "Список кроватей не может быть null: пустой список — нет кроватей");
        Objects.requireNonNull(taken, "Занятые кровати не могут быть null: пустое множество — все свободны");
        Objects.requireNonNull(from, "Точка отсчёта не может быть null: по ней ищется ближайшая кровать");
        Objects.requireNonNull(home, "Дом не может быть null: поселение строится вокруг дома духа");
        if (settlementRadius < 1) {
            throw new IllegalArgumentException(
                    "Радиус поселения " + settlementRadius + " меньше 1: с ним ни одна кровать не считалась бы своей");
        }
        if (ownBed.isPresent()) {
            BlockCoord bed = ownBed.get();
            if (beds.contains(bed) && !taken.contains(bed) && inside(bed, home, settlementRadius)) {
                return ownBed;
            }
        }
        Comparator<BlockCoord> nearest =
                Comparator.comparingDouble((BlockCoord bed) -> distanceSquared(bed, from))
                        .thenComparingInt(BlockCoord::x)
                        .thenComparingInt(BlockCoord::y)
                        .thenComparingInt(BlockCoord::z);
        return beds.stream()
                .filter(bed -> bed != null && !taken.contains(bed) && inside(bed, home, settlementRadius))
                .min(nearest);
    }

    private static boolean inside(BlockCoord bed, BlockCoord home, int settlementRadius) {
        return distanceSquared(bed, home) <= (double) settlementRadius * settlementRadius;
    }

    private static double distanceSquared(BlockCoord first, BlockCoord second) {
        double dx = first.x() - second.x();
        double dy = first.y() - second.y();
        double dz = first.z() - second.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
