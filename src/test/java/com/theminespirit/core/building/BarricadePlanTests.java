package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class BarricadePlanTests {
    @Test
    void seal_BanksThreeApart_SeamThreeByHeight() {
        List<BlockCoord> seam = BarricadePlan.seal(new BlockCoord(0, 64, 0), new BlockCoord(2, 64, 0), 2,
                new BlockCoord(9, 64, 9));

        assertEquals(6, seam.size(),
                "Шов через проход шириной 3 и высотой 2 — это 6 клеток: иначе в баррикаде дыра и враг проходит");
        assertTrue(seam.contains(new BlockCoord(1, 64, 0)) && seam.contains(new BlockCoord(1, 65, 0)),
                "Шов обязан перекрывать середину прохода на всю высоту: иначе запечатан только край");
    }

    @Test
    void seal_BanksOnOneLine_Throws() {
        BlockCoord bank = new BlockCoord(0, 64, 0);

        assertThrows(IllegalArgumentException.class,
                () -> BarricadePlan.seal(bank, bank, 2, new BlockCoord(5, 64, 5)),
                "Сошедшиеся берега — не проход: шов не из чего строить, а молчание даст пустую стройку");
    }

    @Test
    void seal_SpiritInsideSeam_SpiritCellExcluded() {
        BlockCoord spirit = new BlockCoord(1, 64, 0);

        List<BlockCoord> seam = BarricadePlan.seal(new BlockCoord(0, 64, 0), new BlockCoord(2, 64, 0), 2, spirit);

        assertTrue(!seam.contains(spirit),
                "Клетка духа обязана выпадать из шва: иначе строитель замуровывает себя первым же блоком");
        assertEquals(5, seam.size(),
                "Без клетки духа шов 3×2 даёт 5 клеток: потеря другой клетки — дыра в баррикаде");
    }

    @Test
    void seal_InvalidHeight_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> BarricadePlan.seal(new BlockCoord(0, 64, 0), new BlockCoord(2, 64, 0), 0,
                        new BlockCoord(9, 64, 9)),
                "Нулевая высота шва обязана отвергаться: шов ниже блока врага не остановит");
    }

    @Test
    void seal_BottomUpOrder_LowerBeforeUpper() {
        List<BlockCoord> seam = BarricadePlan.seal(new BlockCoord(0, 64, 0), new BlockCoord(2, 64, 0), 2,
                new BlockCoord(9, 64, 9));

        int lastLower = seam.lastIndexOf(new BlockCoord(2, 64, 0));
        int firstUpper = seam.indexOf(new BlockCoord(0, 65, 0));
        assertTrue(lastLower < firstUpper,
                "Нижний ряд шва обязан идти раньше верхнего: иначе верх ставится без опоры");
    }

    @Test
    void seal_AnyBanks_HasNoDuplicates() {
        List<BlockCoord> seam = BarricadePlan.seal(new BlockCoord(0, 60, 7), new BlockCoord(0, 60, 10), 3,
                new BlockCoord(9, 64, 9));

        assertEquals(seam.size(), new HashSet<>(seam).size(),
                "Дубли в шве — двойной расход блоков: каждая клетка ставится один раз");
    }
}
