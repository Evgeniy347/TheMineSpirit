package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.CatalogLimits;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class BuildBoxTests {
    @Test
    void of_ReversedCorners_SameBox() {
        BuildBox forward = BuildBox.of(1, 2, 3, 4, 6, 8);
        BuildBox reversed = BuildBox.of(4, 6, 8, 1, 2, 3);

        assertEquals(forward, reversed,
                "Коробка обязана нормализовать порядок углов: стратег задаёт углы в любом порядке, иначе коробка зависит от порядка");
    }

    @Test
    void volume_SingleAndBox_MatchesSides() {
        assertEquals(1L, BuildBox.of(5, 5, 5, 5, 5, 5).volume(),
                "Коробка 1×1×1 обязана иметь объём 1: иначе счёт лимита объёма врёт и стройка либо запрещена, либо безлимитна");
        assertEquals(24L, BuildBox.of(0, 0, 0, 1, 2, 3).volume(),
                "Коробка 2×3×4 обязана иметь объём 24: иначе лимит BUILD_VOLUME_MAX проверяет не то число");
    }

    @Test
    void cellsBottomUp_LowerLayer_PrecedesUpper() {
        List<BlockCoord> ordered = BuildBox.of(0, 0, 0, 1, 1, 1).cellsBottomUp();

        for (int i = 1; i < ordered.size(); i++) {
            assertTrue(ordered.get(i - 1).y() <= ordered.get(i).y(),
                    "Порядок постановки обязан идти снизу вверх: верхний блок без опоры снизу обвалится или повиснет");
        }
    }

    @Test
    void cellsBottomUp_FullBox_IsPermutationWithoutDuplicates() {
        BuildBox box = BuildBox.of(0, 0, 0, 1, 2, 1);

        assertEquals(new HashSet<>(box.cells()), new HashSet<>(box.cellsBottomUp()),
                "Список снизу вверх обязан содержать те же ячейки, что cells(): потеря или дубль ячейки даёт дырку или двойной расход");
        assertEquals(box.cells().size(), new HashSet<>(box.cellsBottomUp()).size(),
                "Список снизу вверх обязан идти без дублей: дубль ячейки ставит блок дважды и тратит материал");
    }

    @Test
    void exceeds_VolumeAboveLimit_IsDetected() {
        BuildBox box = BuildBox.cube(0, 0, 0, 16);

        assertTrue(box.exceeds(CatalogLimits.BUILD_VOLUME_MAX - 1),
                "Объём сверх предела обязан ловиться до первого блока: иначе дух начнёт стройку, которую запрет оборвёт на середине");
        assertTrue(!BuildBox.of(0, 0, 0, 1, 1, 1).exceeds(CatalogLimits.BUILD_VOLUME_MAX),
                "Маленькая коробка обязана проходить лимит: иначе запрет бьёт по любой стройке");
    }

    @Test
    void cube_SizeTwo_CoversEightCells() {
        BuildBox box = BuildBox.cube(3, 5, 7, 2);

        assertEquals(8L, box.volume(),
                "Куб 2×2×2 от угла обязан покрыть 8 клеток: иначе dig_area копает не ту область");
        assertTrue(box.contains(new BlockCoord(4, 6, 8)),
                "Куб обязан включать дальний угол: иначе край области остаётся нетронутым");
    }
}
