package com.theminespirit.core.observation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

class SectionCursorProperties {
    @Provide
    Arbitrary<BlockBox> boxes() {
        Arbitrary<Integer> start = Arbitraries.integers().between(-100, 100);
        Arbitrary<Integer> size = Arbitraries.integers().between(0, 40);
        return Combinators.combine(start, start, start, size, size, size)
                .as((x, y, z, dx, dy, dz) ->
                        new BlockBox(new BlockCoord(x, y, z), new BlockCoord(x + dx, y + dy, z + dz)));
    }

    @Provide
    Arbitrary<BlockCoord> centers() {
        Arbitrary<Integer> coordinate = Arbitraries.integers().between(-120, 120);
        return Combinators.combine(coordinate, coordinate, coordinate).as(BlockCoord::new);
    }

    @Property
    void next_RandomCube_SlicesCoverCubeExactlyWithoutOverlap(
            @ForAll("boxes") BlockBox box, @ForAll("centers") BlockCoord center) {
        List<SectionSlice> slices = drain(SectionCursor.of(box, center), 7).stream()
                .flatMap(List::stream)
                .toList();

        long total = slices.stream().mapToLong(SectionSlice::volume).sum();
        assertEquals(
                box.volume(),
                total,
                "Сумма объёмов срезов обязана равняться объёму куба " + box
                        + ": иначе блоки пропущены или посчитаны дважды");
        for (int i = 0; i < slices.size(); i++) {
            assertTrue(
                    contained(box, slices.get(i).bounds()),
                    "Срез " + slices.get(i) + " выходит за куб " + box + ": скан прочитал бы блоки вне радиуса");
            for (int j = i + 1; j < slices.size(); j++) {
                assertTrue(
                        !intersects(slices.get(i).bounds(), slices.get(j).bounds()),
                        "Срезы " + slices.get(i) + " и " + slices.get(j)
                                + " пересекаются: блоки на стыке посчитаны дважды");
            }
        }
    }

    @Property
    void next_RandomCube_SlicesDoNotDecreaseInDistance(
            @ForAll("boxes") BlockBox box, @ForAll("centers") BlockCoord center) {
        List<SectionSlice> slices = drain(SectionCursor.of(box, center), 5).stream()
                .flatMap(List::stream)
                .toList();

        for (int i = 1; i < slices.size(); i++) {
            assertTrue(
                    slices.get(i - 1).distanceSquared(center) <= slices.get(i).distanceSquared(center),
                    "Срез " + slices.get(i) + " ближе к центру " + center + ", чем предыдущий " + slices.get(i - 1)
                            + ": ближние срезы обязаны идти первыми, иначе поиск блока сначала читает дальнее");
        }
    }

    @Property
    void next_RandomPortionSize_PortionsStayWithinLimitAndDrainInCeilCalls(
            @ForAll("boxes") BlockBox box,
            @ForAll("centers") BlockCoord center,
            @ForAll @IntRange(min = 1, max = 20) int maxSlices) {
        SectionCursor cursor = SectionCursor.of(box, center);
        int total = cursor.total();

        List<List<SectionSlice>> portions = drain(cursor, maxSlices);

        assertEquals(
                (total + maxSlices - 1) / maxSlices,
                portions.size(),
                "Курсор из " + total + " срезов с порцией " + maxSlices
                        + " обязан опустеть за ceil(N / порция) вызовов");
        for (List<SectionSlice> portion : portions) {
            assertTrue(
                    portion.size() <= maxSlices && !portion.isEmpty(),
                    "Порция из " + portion.size() + " срезов нарушает предел " + maxSlices
                            + ": лимит порции держит стоимость тика");
        }
    }

    private static List<List<SectionSlice>> drain(SectionCursor cursor, int maxSlices) {
        List<List<SectionSlice>> portions = new ArrayList<>();
        while (!cursor.isDone()) {
            portions.add(cursor.next(maxSlices));
        }
        return portions;
    }

    private static boolean contained(BlockBox outer, BlockBox inner) {
        return outer.contains(inner.min()) && outer.contains(inner.max());
    }

    private static boolean intersects(BlockBox a, BlockBox b) {
        return a.min().x() <= b.max().x()
                && b.min().x() <= a.max().x()
                && a.min().y() <= b.max().y()
                && b.min().y() <= a.max().y()
                && a.min().z() <= b.max().z()
                && b.min().z() <= a.max().z();
    }
}
