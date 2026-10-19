package com.theminespirit.core.sleep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

class BedChoiceProperties {
    private static final BlockCoord HOME = new BlockCoord(0, 64, 0);
    private static final int RADIUS = 32;

    @Provide
    Arbitrary<Set<BlockCoord>> beds() {
        Arbitrary<BlockCoord> bed = Combinators.combine(
                        Arbitraries.integers().between(-RADIUS, RADIUS),
                        Arbitraries.integers().between(-RADIUS, RADIUS))
                .as((x, z) -> new BlockCoord(x, 64, z));
        return bed.set().ofMaxSize(6);
    }

    @Property
    void choose_SequentialSpirits_NeverShareBed(
            @ForAll("beds") Set<BlockCoord> beds, @ForAll @IntRange(min = 1, max = 8) int spiritCount) {
        Set<BlockCoord> taken = new HashSet<>();
        List<BlockCoord> assigned = new ArrayList<>();
        for (int i = 0; i < spiritCount; i++) {
            BlockCoord from = new BlockCoord(i * 7, 64, i * 5);
            Optional<BlockCoord> bed = BedChoice.choose(Optional.empty(), beds, taken, from, HOME, RADIUS);
            if (bed.isPresent()) {
                assigned.add(bed.get());
                taken.add(bed.get());
            }
        }

        assertEquals(
                assigned.size(),
                new LinkedHashSet<>(assigned).size(),
                "Двое духов легли в одну кровать: выбор обязан считать занятую кровать занятой");
        assertTrue(
                beds.containsAll(assigned),
                "Выбранная кровать обязана быть среди найденных в мире: иначе дух ляжет в несуществующую кровать");
    }
}
