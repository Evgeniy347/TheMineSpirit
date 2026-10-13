package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShelterPlanTests {
    @Test
    void shelterSide_DeclaredValue_EqualsFour() {
        assertEquals(4, ShelterPlan.SHELTER_SIDE,
                "Укрытие 4×4: меньше — дух не встанет внутри, больше — съедает бюджет и время стройки");
    }

    @Test
    void shelter_FullBudget_WallCellsPerTier() {
        List<BlockCoord> cells = ShelterPlan.shelter(new BlockCoord(0, 64, 0), 2, 64);

        assertEquals(24, cells.size(),
                "Стены 4×4 высотой 2 — это 12 клеток на ярус, 24 всего: иначе в стене дыра или лишний расход");
        long lower = cells.stream().filter(cell -> cell.y() == 64).count();
        long upper = cells.stream().filter(cell -> cell.y() == 65).count();
        assertEquals(12, lower,
                "Нижний ярус обязан держать 12 клеток периметра: иначе стена начинается с дыры");
        assertEquals(12, upper,
                "Верхний ярус обязан держать 12 клеток периметра: иначе мобы перепрыгивают стену");
    }

    @Test
    void shelter_FullBudget_InteriorEmpty() {
        List<BlockCoord> cells = ShelterPlan.shelter(new BlockCoord(0, 64, 0), 2, 64);

        for (int dx = 1; dx <= 2; dx++) {
            for (int dz = 1; dz <= 2; dz++) {
                BlockCoord inner = new BlockCoord(dx, 64, dz);
                assertTrue(!cells.contains(inner),
                        "Интерьер 2×2 обязан оставаться пустым: иначе дух замурован внутри своей коробки");
            }
        }
    }

    @Test
    void shelter_SmallBudget_TruncatesWithoutError() {
        List<BlockCoord> cells = ShelterPlan.shelter(new BlockCoord(0, 64, 0), 2, 5);

        assertEquals(5, cells.size(),
                "Малый бюджет обязан усекать план, а не ронять стройку: иначе дух с пустым карманом вешает тик");
        assertTrue(cells.stream().allMatch(cell -> cell.y() == 64),
                "Усечение обязано идти снизу вверх: верх без опоры строителю не поставить");
    }

    @Test
    void shelter_BottomUpOrder_LowerBeforeUpper() {
        List<BlockCoord> cells = ShelterPlan.shelter(new BlockCoord(0, 64, 0), 2, 64);

        int firstUpper = cells.indexOf(new BlockCoord(0, 65, 0));
        int lastLower = cells.lastIndexOf(new BlockCoord(3, 64, 3));
        assertTrue(lastLower < firstUpper,
                "Нижний ярус обязан идти раньше верхнего: иначе верхний ставится без опоры");
    }

    @Test
    void shelter_InvalidInput_Throws() {
        assertThrows(NullPointerException.class, () -> ShelterPlan.shelter(null, 2, 64),
                "Укрытие без угла обязано отвергаться: без привязки стенам негде встать");
        assertThrows(IllegalArgumentException.class,
                () -> ShelterPlan.shelter(new BlockCoord(0, 64, 0), 0, 64),
                "Нулевая высота стен обязана отвергаться: стена ниже блока не прикроет духа");
        assertThrows(IllegalArgumentException.class,
                () -> ShelterPlan.shelter(new BlockCoord(0, 64, 0), 2, 0),
                "Нулевой бюджет обязан отвергаться: без блоков строителю нечего ставить");
    }

    @Test
    void shelter_AnyGeometry_HasNoDuplicates() {
        List<BlockCoord> cells = ShelterPlan.shelter(new BlockCoord(-5, 60, 7), 3, 64);

        assertEquals(cells.size(), new HashSet<>(cells).size(),
                "Дубли в плане — двойной расход блоков: каждая клетка ставится один раз");
    }
}
