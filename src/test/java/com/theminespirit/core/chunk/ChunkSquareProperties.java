package com.theminespirit.core.chunk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.statistics.Statistics;

class ChunkSquareProperties {
    @Property
    void chunks_AnyRadius_ContainsExactSquareSize(@ForAll @IntRange(min = 0, max = 32) int radius) {
        Set<ChunkCoord> chunks = ChunkSquare.chunks(new ChunkCoord(0, 0), radius);

        assertEquals(
                (2 * radius + 1) * (2 * radius + 1),
                chunks.size(),
                "Для любого радиуса от 0 до 32 размер квадрата обязан быть (2R+1)^2: иначе дух держит "
                        + "не ту зону, а на границе чанк либо теряется, либо грузится лишний");
    }

    @Property
    void diff_AnyCenters_RemovesAndAddsToReachNewSquare(
            @ForAll("diffCases") Tuple.Tuple3<ChunkCoord, ChunkCoord, Integer> sample) {
        ChunkCoord oldCenter = sample.get1();
        ChunkCoord newCenter = sample.get2();
        int radius = sample.get3();
        ChunkDiff diff = ChunkSquare.diff(oldCenter, newCenter, radius);

        Set<ChunkCoord> applied = ChunkSquare.apply(ChunkSquare.chunks(oldCenter, radius), diff);

        assertEquals(
                ChunkSquare.chunks(newCenter, radius),
                applied,
                "Применённый diff обязан переводить старый квадрат в новый при любых центрах: билеты после "
                        + "сдвига должны совпадать с зоной вокруг духа, ни больше ни меньше");

        Set<ChunkCoord> intersection = new HashSet<>(ChunkSquare.chunks(oldCenter, radius));
        intersection.retainAll(ChunkSquare.chunks(newCenter, radius));
        boolean overlaps = !intersection.isEmpty();
        Statistics.label("пересекаются").collect(overlaps);
        Statistics.collect(overlaps);
        Statistics.coverage(coverage -> coverage.check(true).percentage(p -> p >= 30.0));
    }

    @Provide
    Arbitrary<Tuple.Tuple3<ChunkCoord, ChunkCoord, Integer>> diffCases() {
        return Arbitraries.integers().between(0, 16).flatMap(radius -> {
            int span = 2 * radius + 1;
            return anyCenter().flatMap(old -> {
                Arbitrary<ChunkCoord> nearbyCenter = Combinators.combine(
                                Arbitraries.integers().between(-span, span),
                                Arbitraries.integers().between(-span, span))
                        .as((dx, dz) -> new ChunkCoord(old.x() + dx, old.z() + dz));
                Arbitrary<ChunkCoord> newCenter =
                        Arbitraries.frequencyOf(Tuple.of(9, nearbyCenter), Tuple.of(1, anyCenter()));
                return Combinators.combine(Arbitraries.just(old), newCenter)
                        .as((fixed, fresh) -> Tuple.of(fixed, fresh, radius));
            });
        });
    }

    @Property
    void diff_WalkOfSteps_CountsOneRowAddedAndRemovedPerStep(
            @ForAll @IntRange(min = 1, max = 64) int steps, @ForAll @IntRange(min = 0, max = 10) int radius) {
        Set<ChunkCoord> current = ChunkSquare.chunks(new ChunkCoord(0, 0), radius);
        int added = 0;
        int removed = 0;
        for (int step = 1; step <= steps; step++) {
            ChunkDiff diff = ChunkSquare.diff(new ChunkCoord(step - 1, 0), new ChunkCoord(step, 0), radius);
            added += diff.toAdd().size();
            removed += diff.toRemove().size();
            current = ChunkSquare.apply(current, diff);
        }
        int row = 2 * radius + 1;

        assertEquals(
                steps * row,
                added,
                "N переходов на соседний чанк обязаны дать ровно N*(2R+1) добавлений: иначе набор не "
                        + "сдвигается, и дух тянет за собой старые чанки");
        assertEquals(
                steps * row,
                removed,
                "N переходов на соседний чанк обязаны дать ровно N*(2R+1) снятий: иначе ушедшие чанки "
                        + "остаются нагруженными и утекут");
        assertEquals(
                ChunkSquare.chunks(new ChunkCoord(steps, 0), radius),
                current,
                "После N переходов удержанный набор обязан равняться квадрату вокруг конечного центра");
    }

    private static Arbitrary<ChunkCoord> anyCenter() {
        return Combinators.combine(
                        Arbitraries.integers().between(-1000, 1000),
                        Arbitraries.integers().between(-1000, 1000))
                .as(ChunkCoord::new);
    }
}
