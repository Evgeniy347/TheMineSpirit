package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

class CellPartitionProperties {
    @Provide
    Arbitrary<List<BlockCoord>> cells() {
        return Arbitraries.integers().between(-10, 10)
                .flatMap(x -> Arbitraries.integers().between(-10, 10)
                        .flatMap(y -> Arbitraries.integers().between(-10, 10)
                                .map(z -> new BlockCoord(x, y, z))))
                .list()
                .ofMaxSize(30);
    }

    @Property
    void split_AnyCellsAnyParts_DisjointAndFullCoverage(@ForAll("cells") List<BlockCoord> cells,
            @ForAll @IntRange(min = 1, max = 6) int parts) {
        List<List<BlockCoord>> shares = CellPartition.split(cells, parts);

        assertEquals(parts, shares.size(), "Долей ровно столько, сколько частей");

        List<BlockCoord> merged = new ArrayList<>();
        for (List<BlockCoord> share : shares) {
            merged.addAll(share);
        }
        assertEquals(cells.size(), merged.size(),
                "Доли вместе содержат столько клеток, сколько во входе: клетка не потеряна и не удвоена, то есть "
                        + "доли не пересекаются");
        assertEquals(new HashSet<>(cells), new HashSet<>(merged),
                "Объединение долей совпадает со входом: полнота разбиения");
    }

    @Property
    void split_AnyCellsAnyParts_Deterministic(@ForAll("cells") List<BlockCoord> cells,
            @ForAll @IntRange(min = 1, max = 6) int parts) {
        assertEquals(CellPartition.split(cells, parts), CellPartition.split(cells, parts),
                "Тот же вход даёт то же разбиение: доли стабильны между пересборками отряда");
    }
}
