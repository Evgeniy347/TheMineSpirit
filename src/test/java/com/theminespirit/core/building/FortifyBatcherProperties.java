package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class FortifyBatcherProperties {
    @Provide
    Arbitrary<List<BlockCoord>> columns() {
        return Arbitraries.integers().between(0, 30).map(height -> {
            List<BlockCoord> cells = new ArrayList<>();
            for (int level = 0; level < height; level++) {
                cells.add(new BlockCoord(0, 64 + level, 0));
            }
            return cells;
        });
    }

    @Provide
    Arbitrary<Integer> portions() {
        return Arbitraries.integers().between(1, 8);
    }

    @Property
    void batches_AnyInput_ConcatEqualsInput(@ForAll("columns") List<BlockCoord> cells,
            @ForAll("portions") int perTick) {
        List<List<BlockCoord>> batches = FortifyBatcher.batches(cells, perTick);

        List<BlockCoord> joined = new ArrayList<>();
        for (List<BlockCoord> batch : batches) {
            assertTrue(batch.size() <= perTick,
                    "Порция обязана держать не больше предела за тик: иначе стройка грузит серверный тик");
            joined.addAll(batch);
        }
        assertEquals(cells, joined,
                "Склейка порций обязана давать вход: потеря клетки — дыра, перестановка — блок без опоры");
    }

    @Property
    void batches_AnyInput_BottomUpPreserved(@ForAll("columns") List<BlockCoord> cells,
            @ForAll("portions") int perTick) {
        List<List<BlockCoord>> batches = FortifyBatcher.batches(cells, perTick);

        List<BlockCoord> joined = new ArrayList<>();
        for (List<BlockCoord> batch : batches) {
            joined.addAll(batch);
        }
        for (int index = 1; index < joined.size(); index++) {
            assertTrue(joined.get(index - 1).y() <= joined.get(index).y(),
                    "Порядок снизу вверх обязан сохраняться через порции: иначе верх ставится без опоры");
        }
    }

    @Property
    void batches_SameInput_TwiceSameResult(@ForAll("columns") List<BlockCoord> cells,
            @ForAll("portions") int perTick) {
        assertEquals(FortifyBatcher.batches(cells, perTick), FortifyBatcher.batches(cells, perTick),
                "Тот же вход даёт те же порции: иначе исполнитель и бронь расходятся");
    }
}
