package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DismantlePlanTests {
    @Test
    void dismantle_MixedCells_OnlyOwn() {
        BlockCoord ownLower = new BlockCoord(0, 64, 0);
        BlockCoord alien = new BlockCoord(1, 64, 0);
        BlockCoord ownUpper = new BlockCoord(0, 65, 0);
        Set<BlockCoord> marked = Set.of(ownLower, ownUpper);

        List<BlockCoord> removed = DismantlePlan.dismantle(List.of(ownLower, alien, ownUpper), marked::contains);

        assertEquals(List.of(ownUpper, ownLower), removed,
                "Разбор обязан снимать только свои блоки: чужой блок трогать нельзя, иначе дух ломает мир игрока");
    }

    @Test
    void dismantle_BottomUpInput_TopDownOutput() {
        BlockCoord lower = new BlockCoord(0, 64, 0);
        BlockCoord middle = new BlockCoord(0, 65, 0);
        BlockCoord upper = new BlockCoord(0, 66, 0);

        List<BlockCoord> removed = DismantlePlan.dismantle(List.of(lower, middle, upper), cell -> true);

        assertEquals(List.of(upper, middle, lower), removed,
                "Разбор обязан идти сверху вниз: иначе верх зависнет без опоры и упадёт на духа");
    }

    @Test
    void dismantle_ShelterWithRoof_RoofFirst() {
        List<BlockCoord> shelter = ShelterPlan.shelter(new BlockCoord(0, 64, 0), 2, 64);

        List<BlockCoord> removed = DismantlePlan.dismantle(shelter, cell -> true);

        assertEquals(shelter.size(), removed.size(),
                "Разбор обязан снимать все свои клетки укрытия: потерянная крыша захламляет мир похода");
        int roofLevel = 64 + 2;
        int lastRoof = -1;
        int firstWall = removed.size();
        for (int index = 0; index < removed.size(); index++) {
            if (removed.get(index).y() == roofLevel) {
                lastRoof = Math.max(lastRoof, index);
            } else {
                firstWall = Math.min(firstWall, index);
            }
        }
        assertTrue(lastRoof < firstWall,
                "Разбор обязан снимать крышу раньше стен: иначе снятая стена роняет крышу духу на голову");
    }

    @Test
    void dismantle_EmptyInput_GivesEmpty() {
        assertEquals(List.of(), DismantlePlan.dismantle(List.of(), cell -> true),
                "Пустой вход обязан давать пустой разбор: иначе дух разбирает то, чего нет");
    }

    @Test
    void dismantle_NullInput_Throws() {
        assertThrows(NullPointerException.class, () -> DismantlePlan.dismantle(null, cell -> true),
                "Разбор без списка обязан отвергаться: без него неизвестно, что снимать");
        assertThrows(NullPointerException.class,
                () -> DismantlePlan.dismantle(List.of(new BlockCoord(0, 64, 0)), null),
                "Разбор без проверки своих обязан отвергаться: без неё дух тронет чужие блоки");
    }

    @Test
    void dismantle_AllAlien_GivesEmpty() {
        List<BlockCoord> cells = List.of(new BlockCoord(0, 64, 0), new BlockCoord(0, 65, 0));

        assertEquals(List.of(), DismantlePlan.dismantle(cells, cell -> false),
                "Без своих блоков разбирать нечего: иначе дух снесёт чужую постройку");
    }

    @Test
    void dismantle_AnyResult_SubsetOfInput() {
        List<BlockCoord> cells = List.of(new BlockCoord(0, 64, 0), new BlockCoord(1, 64, 0));

        List<BlockCoord> removed = DismantlePlan.dismantle(cells, cell -> cell.x() == 0);

        assertTrue(cells.containsAll(removed),
                "Разбор обязан быть подмножеством входа: иначе дух снимает блоки мимо плана");
    }
}
