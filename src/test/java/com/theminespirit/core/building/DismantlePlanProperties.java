package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class DismantlePlanProperties {
    @Provide
    Arbitrary<List<BlockCoord>> columns() {
        return Arbitraries.integers().between(0, 20).map(height -> {
            List<BlockCoord> cells = new ArrayList<>();
            for (int level = 0; level < height; level++) {
                cells.add(new BlockCoord(0, 64 + level, 0));
            }
            return cells;
        });
    }

    @Provide
    Arbitrary<Set<Integer>> keptLevels() {
        return Arbitraries.integers().between(0, 19).set().ofMaxSize(20);
    }

    @Property
    void dismantle_AnyInput_OnlyOwnReversedSubset(@ForAll("columns") List<BlockCoord> cells,
            @ForAll("keptLevels") Set<Integer> kept) {
        List<BlockCoord> removed = DismantlePlan.dismantle(cells, cell -> kept.contains(cell.y() - 64));

        assertTrue(new HashSet<>(cells).containsAll(removed),
                "Разбор обязан быть подмножеством входа: иначе дух снимает блоки мимо плана");
        for (BlockCoord cell : removed) {
            assertTrue(kept.contains(cell.y() - 64),
                    "Каждая снятая клетка обязана проходить проверку своих: иначе дух тронет чужой блок");
        }
        assertEquals(removed.size(), new HashSet<>(removed).size(),
                "Дубли в разборе — двойная работа: каждая клетка снимается один раз");
        List<BlockCoord> expected = new ArrayList<>();
        for (int index = cells.size() - 1; index >= 0; index--) {
            BlockCoord cell = cells.get(index);
            if (kept.contains(cell.y() - 64) && !expected.contains(cell)) {
                expected.add(cell);
            }
        }
        assertEquals(expected, removed,
                "Разбор обязан идти в обратном входу порядке: сверху вниз, иначе верх зависнет без опоры");
    }

    @Property
    void dismantle_SameInput_TwiceSameResult(@ForAll("columns") List<BlockCoord> cells,
            @ForAll("keptLevels") Set<Integer> kept) {
        assertEquals(DismantlePlan.dismantle(cells, cell -> kept.contains(cell.y() - 64)),
                DismantlePlan.dismantle(cells, cell -> kept.contains(cell.y() - 64)),
                "Тот же вход даёт тот же разбор: иначе исполнитель и бронь расходятся");
    }
}
