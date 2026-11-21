package com.theminespirit.core.perception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class HazardFactsProperties {
    @Provide
    Arbitrary<List<HazardRef>> scenes() {
        Arbitrary<BlockCoord> pos = Combinators.combine(
                        Arbitraries.integers().between(-20, 20),
                        Arbitraries.integers().between(55, 70),
                        Arbitraries.integers().between(-20, 20))
                .as(BlockCoord::new);
        Arbitrary<HazardRef> hazard = Combinators.combine(
                        Arbitraries.of(HazardType.class),
                        pos,
                        Arbitraries.integers().between(0, 6).map(Integer::doubleValue))
                .as(HazardRef::new);
        return hazard.list().ofMaxSize(25);
    }

    @Property
    void nearest_AnyScene_HasMinimalDistanceOrIsNullWhenEmpty(@ForAll("scenes") List<HazardRef> hazards) {
        HazardFacts facts = new HazardFacts(hazards);

        if (hazards.isEmpty()) {
            assertNull(
                    facts.nearest(),
                    "в пустой сцене ближайшей опасности быть не должно"
                            + " Иначе тест nearest_AnyScene_HasMinimalDistanceOrIsNullWhenEmpty перестанет ловить эту поломку. При смене nearest_AnyScene_HasMinimalDistanceOrIsNullWhenEmpty проверь это утверждение.");
            return;
        }
        double minimum = hazards.stream().mapToDouble(HazardRef::distance).min().orElseThrow();
        assertEquals(
                minimum,
                facts.nearest().distance(),
                0.0,
                "nearest обязан иметь наименьшее расстояние среди опасностей сцены"
                        + " Иначе тест nearest_AnyScene_HasMinimalDistanceOrIsNullWhenEmpty перестанет ловить эту поломку. При смене nearest_AnyScene_HasMinimalDistanceOrIsNullWhenEmpty проверь это утверждение.");
        assertTrue(
                hazards.contains(facts.nearest()),
                "nearest обязан быть одной из опасностей сцены"
                        + " Иначе тест nearest_AnyScene_HasMinimalDistanceOrIsNullWhenEmpty перестанет ловить эту поломку. При смене nearest_AnyScene_HasMinimalDistanceOrIsNullWhenEmpty проверь это утверждение.");
    }

    @Property
    void nearest_ShuffledScene_StaysTheSame(@ForAll("scenes") List<HazardRef> hazards, @ForAll long seed) {
        List<HazardRef> shuffled = new ArrayList<>(hazards);
        Collections.shuffle(shuffled, new Random(seed));

        assertEquals(
                new HazardFacts(hazards).nearest(),
                new HazardFacts(shuffled).nearest(),
                "порядок опасностей в списке не должен влиять на ближайшую: сцена одна и та же"
                        + " При смене nearest_ShuffledScene_StaysTheSame проверь это утверждение.");
    }
}
