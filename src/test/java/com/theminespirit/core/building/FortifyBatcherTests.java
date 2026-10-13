package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FortifyBatcherTests {
    private static List<BlockCoord> column(int size) {
        List<BlockCoord> cells = new ArrayList<>();
        for (int level = 0; level < size; level++) {
            cells.add(new BlockCoord(0, 64 + level, 0));
        }
        return cells;
    }

    @Test
    void batches_FiveCellsByTwo_ThreePortionsTwoTwoOne() {
        List<List<BlockCoord>> portions = FortifyBatcher.batches(column(5), 2);

        assertEquals(3, portions.size(),
                "5 клеток по 2 обязаны давать 3 порции: иначе строитель растянет стройку или превысит тик");
        assertEquals(2, portions.get(0).size(),
                "Первая порция обязана держать 2 блока: иначе тик недогружен без причины");
        assertEquals(2, portions.get(1).size(),
                "Вторая порция обязана держать 2 блока: иначе порядок постановки съезжает");
        assertEquals(1, portions.get(2).size(),
                "Хвост обязан держать 1 блок: потеря хвоста — дыра в укреплении");
    }

    @Test
    void batches_AnyInput_PreservesOrder() {
        List<BlockCoord> cells = column(5);

        List<BlockCoord> joined = new ArrayList<>();
        for (List<BlockCoord> portion : FortifyBatcher.batches(cells, 2)) {
            joined.addAll(portion);
        }

        assertEquals(cells, joined,
                "Склейка порций обязана давать входной порядок: иначе низ ставится после верха и висит без опоры");
    }

    @Test
    void batches_InvalidPerTick_Throws() {
        assertThrows(IllegalArgumentException.class, () -> FortifyBatcher.batches(column(3), 0),
                "Нулевая порция обязана отвергаться: за тик ставится хотя бы один блок");
        assertThrows(NullPointerException.class, () -> FortifyBatcher.batches(null, 2),
                "Порции без плана обязаны отвергаться: без клеток неизвестно, что ставить за тик");
    }

    @Test
    void selfBuried_OccupiedCell_ReportedDeferred() {
        BlockCoord feet = new BlockCoord(0, 64, 0);
        List<BlockCoord> cells = List.of(new BlockCoord(0, 63, 0), feet);

        List<BlockCoord> deferred = FortifyBatcher.selfBuried(cells, Set.of(feet));

        assertEquals(List.of(feet), deferred,
                "Занятая духом клетка обязана откладываться: иначе строитель ставит блок себе в ноги");
    }

    @Test
    void selfBuried_FreeCells_NothingDeferred() {
        List<BlockCoord> cells = column(3);

        assertEquals(List.of(), FortifyBatcher.selfBuried(cells, Set.of()),
                "Свободные клетки откладывать нечего: иначе стройка встанет без занятых клеток");
    }

    @Test
    void batches_OccupiedFirst_ExcludedFromFirstPortion() {
        BlockCoord feet = new BlockCoord(0, 64, 0);
        List<BlockCoord> ordered = BuildOrder.placement(column(3), Set.of(feet));

        List<BlockCoord> first = FortifyBatcher.batches(ordered, 2).get(0);

        assertTrue(!first.contains(feet),
                "Занятая клетка обязана уходить из первой порции в хвост: иначе дух замуровывает себя первым тиком");
    }
}
