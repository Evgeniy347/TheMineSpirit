package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class VeinWalkerTests {
    @Test
    void collect_LineVein_ReturnsEveryBlock() {
        CountingOreView view = new CountingOreView();
        for (int x = 0; x < 5; x++) {
            view.withOre(x, 0, 0);
        }

        Vein vein = VeinWalker.collect(new BlockCoord(0, 0, 0), view, MiningTuning.VEIN_MAX_BLOCKS);

        assertEquals(Set.of(new BlockCoord(0, 0, 0), new BlockCoord(1, 0, 0), new BlockCoord(2, 0, 0),
                        new BlockCoord(3, 0, 0), new BlockCoord(4, 0, 0)), new HashSet<>(vein.blocks()),
                "Жила-линия обходится целиком: пропущенный блок руды остаётся в мире и count не набирается");
        assertFalse(vein.truncated(),
                "Жила короче предела не усечена: truncated=true заставил бы искать следующую жилу раньше времени");
    }

    @Test
    void collect_CubeVein_ReturnsAllTwentySevenBlocks() {
        CountingOreView view = new CountingOreView();
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 3; z++) {
                    view.withOre(x, y, z);
                }
            }
        }

        Vein vein = VeinWalker.collect(new BlockCoord(1, 1, 1), view, MiningTuning.VEIN_MAX_BLOCKS);

        assertEquals(27, vein.blocks().size(),
                "Куб 3×3×3 — одна связная жила из 27 блоков: обход по 26 соседям обязан собрать все");
        assertFalse(vein.truncated(),
                "Куб меньше предела обхода не усечён");
    }

    @Test
    void collect_TwoVeinsSeparatedByEmptyCell_DoNotMerge() {
        CountingOreView view = new CountingOreView()
                .withOre(0, 0, 0).withOre(1, 0, 0)
                .withOre(3, 0, 0).withOre(4, 0, 0);

        Vein vein = VeinWalker.collect(new BlockCoord(0, 0, 0), view, MiningTuning.VEIN_MAX_BLOCKS);

        assertEquals(Set.of(new BlockCoord(0, 0, 0), new BlockCoord(1, 0, 0)), new HashSet<>(vein.blocks()),
                "Жилы через пустую клетку не склеиваются: иначе дух уходит к чужой жиле и недобирает count");
    }

    @Test
    void collect_DiagonalNeighbor_IsConnected() {
        CountingOreView view = new CountingOreView()
                .withOre(0, 0, 0).withOre(1, 1, 1).withOre(2, 2, 2);

        Vein vein = VeinWalker.collect(new BlockCoord(0, 0, 0), view, MiningTuning.VEIN_MAX_BLOCKS);

        assertEquals(3, vein.blocks().size(),
                "Диагональная связь входит в жилу (26 соседей): иначе кристаллическая руда распадается на куски");
    }

    @Test
    void collect_VeinLongerThanLimit_TruncatesAtLimit() {
        CountingOreView view = new CountingOreView();
        for (int x = 0; x < 80; x++) {
            view.withOre(x, 0, 0);
        }

        Vein vein = VeinWalker.collect(new BlockCoord(0, 0, 0), view, MiningTuning.VEIN_MAX_BLOCKS);

        assertEquals(MiningTuning.VEIN_MAX_BLOCKS, vein.blocks().size(),
                "Длинная жила усекается по пределу: обход огромной жилы в один заход кладёт серверный тик");
        assertTrue(vein.truncated(),
                "Усечённая жила обязана помечаться: по флагу адаптер продолжает обход после добычи порции");
    }

    @Test
    void collect_StartNotOre_ReturnsEmpty() {
        CountingOreView view = new CountingOreView().withOre(5, 0, 0);

        Vein vein = VeinWalker.collect(new BlockCoord(0, 0, 0), view, MiningTuning.VEIN_MAX_BLOCKS);

        assertEquals(List.of(), vein.blocks(),
                "Старт не руда — пустая жила: обход начинается только с найденной руды");
        assertFalse(vein.truncated(),
                "Пустая жила не усечена");
    }

    @Test
    void collect_NonPositiveLimit_IsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> VeinWalker.collect(new BlockCoord(0, 0, 0), new CountingOreView(), 0),
                "Предел меньше 1 обязан отклоняться: иначе обход не вернёт ни одного блока");
    }

    @Test
    void collect_RepeatedCell_CellReadAtMostOnce() {
        CountingOreView view = new CountingOreView();
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 3; z++) {
                    view.withOre(x, y, z);
                }
            }
        }

        VeinWalker.collect(new BlockCoord(1, 1, 1), view, MiningTuning.VEIN_MAX_BLOCKS);

        assertTrue(view.reads() <= 26 * 27 + 1,
                "Каждая клетка читается не более одного раза (множество посещённых): повторные чтения "
                        + "превращают обход жилы в квадратичную нагрузку. Чтений: " + view.reads());
    }
}
