package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;

class FoodNormProperties {
    @Provide
    Arbitrary<NutritionTable> tables() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(8)
                .flatMap(name -> Arbitraries.doubles().between(0.5, 20).map(value -> Map.entry(name, value)))
                .list()
                .ofMinSize(1)
                .ofMaxSize(6)
                .map(entries -> {
                    Map<String, Double> merged = new HashMap<>();
                    for (Map.Entry<String, Double> entry : entries) {
                        merged.merge(entry.getKey(), entry.getValue(), Math::max);
                    }
                    return new NutritionTable(merged);
                });
    }

    @Property
    void rankScore_AnyTable_SortedDescending(@ForAll("tables") NutritionTable table) {
        List<String> rank = FoodNorm.rankScore(table);

        assertEquals(
                table.nutrition().size(),
                rank.size(),
                "Ранг содержит каждый предмет таблицы ровно раз: иначе часть еды выпадет из приоритета");
        for (int index = 1; index < rank.size(); index++) {
            double prev = table.nutrition().get(rank.get(index - 1));
            double next = table.nutrition().get(rank.get(index));
            assertTrue(
                    prev > next || (prev == next && rank.get(index - 1).compareTo(rank.get(index)) < 0),
                    "Ранг отсортирован по убыванию сытности: иначе менее сытная еда опередит сытную");
        }
    }

    @Property
    void perSoul_LargerPopulation_NeedsNoLess(
            @ForAll("tables") NutritionTable table,
            @ForAll @IntRange(min = 0, max = 40) int population,
            @ForAll @DoubleRange(min = 0.5, max = 3) double days) {
        FoodNeed small = FoodNorm.perSoul(population, days, table, 1.5);
        FoodNeed large = FoodNorm.perSoul(population + 1, days, table, 1.5);

        assertTrue(
                large.items() >= small.items() && large.nutrition() >= small.nutrition(),
                "Нужда монотонна по населению: иначе рост поселения снизит спрос фермы");
    }

    @Property
    void rankScore_RepeatedCalls_Deterministic(@ForAll("tables") NutritionTable table) {
        assertEquals(
                FoodNorm.rankScore(table),
                FoodNorm.rankScore(table),
                "Ранг детерминирован: иначе приоритет еды мигает между пересборками");
    }

    @Property
    void deficit_AnyInputs_NonNegative(
            @ForAll @IntRange(min = 0, max = 40) int adults,
            @ForAll @IntRange(min = 0, max = 40) int children,
            @ForAll @DoubleRange(min = 0.5, max = 3) double days,
            @ForAll @DoubleRange(min = 0, max = 1000) double stock) {
        FoodDeficit deficit = FoodNorm.deficit(adults, children, days, 1.5, 0.75, stock);

        assertTrue(
                deficit.deficit() >= 0, "Дефицит неотрицателен при любом запасе: излишек еды не превращается в долг");
    }

    @Property
    void deficit_LargerPopulation_NeedsNoLess(
            @ForAll @IntRange(min = 0, max = 40) int adults,
            @ForAll @IntRange(min = 0, max = 10) int children,
            @ForAll @DoubleRange(min = 0.5, max = 3) double days,
            @ForAll @DoubleRange(min = 0, max = 1000) double stock) {
        FoodDeficit small = FoodNorm.deficit(adults, children, days, 1.5, 0.75, stock);
        FoodDeficit large = FoodNorm.deficit(adults + 1, children, days, 1.5, 0.75, stock);

        assertTrue(
                large.deficit() >= small.deficit(),
                "Больше духов — дефицит не меньше: иначе рост поселения снизит спрос на еду");
    }
}
