package com.theminespirit.core.perception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.ScanRadius;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

class PerceptionResultProperties {
    @Provide
    Arbitrary<HazardRef> hazard() {
        Arbitrary<BlockCoord> pos = Combinators.combine(Arbitraries.integers().between(-100, 100),
                Arbitraries.integers().between(-64, 320), Arbitraries.integers().between(-100, 100)).as(BlockCoord::new);
        return Combinators.combine(Arbitraries.of(HazardType.class), pos, Arbitraries.doubles().between(0, 64))
                .as(HazardRef::new);
    }

    @Provide
    Arbitrary<List<HazardRef>> hazards() {
        return hazard().list().ofMaxSize(30);
    }

    @Property
    void hazardsResult_AnyHazards_KeepsThemInOrder(@ForAll("hazards") List<HazardRef> hazards,
            @ForAll @IntRange(min = ScanRadius.MIN, max = ScanRadius.MAX) int radius) {
        PerceptionResult result = PerceptionResult.hazards(ScanRadius.of(radius), hazards);

        assertEquals(hazards, result.hazards(), "опасности результата обязаны идти в том порядке, в каком даны");
        assertEquals(PerceptionType.HAZARDS, result.type(), "фабрика hazards обязана выдавать результат типа HAZARDS");
        assertEquals(List.of(), result.blocks(), "в результате опасностей список блоков обязан быть пустым");
        assertEquals(List.of(), result.entities(), "в результате опасностей список сущностей обязан быть пустым");
    }
}
