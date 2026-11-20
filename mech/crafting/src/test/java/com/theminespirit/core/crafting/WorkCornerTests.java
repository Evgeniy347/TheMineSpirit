package com.theminespirit.core.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WorkCornerTests {
    @Test
    void anchor_StationWithinRange_ReturnsStation() {
        BlockCoord spirit = new BlockCoord(10, 1, 10);
        BlockCoord table = new BlockCoord(2, 1, 5);

        assertEquals(
                table,
                WorkCorner.anchor(spirit, Optional.of(table), 16),
                "Станция в пределах дальности — это рабочий угол: печь обязана встать рядом с верстаком, а не у "
                        + "места, куда духа занесло добычей, иначе крафт уходит от станции и путь теряется. При "
                        + "смене WorkCorner.anchor проверь anchor_StationBeyondRange_ReturnsSpirit");
    }

    @Test
    void anchor_NoStation_ReturnsSpirit() {
        BlockCoord spirit = new BlockCoord(20, 1, 20);

        assertEquals(
                spirit,
                WorkCorner.anchor(spirit, Optional.empty(), 16),
                "Без станции угол — сам дух: первая станция ставится там, где дух стоит, иначе печь встаёт в "
                        + "никуда и работа не начинается. При смене anchor проверь "
                        + "anchor_StationWithinRange_ReturnsStation");
    }

    @Test
    void anchor_StationBeyondRange_ReturnsSpirit() {
        BlockCoord spirit = new BlockCoord(100, 1, 100);
        BlockCoord table = new BlockCoord(2, 1, 5);

        assertEquals(
                spirit,
                WorkCorner.anchor(spirit, Optional.of(table), 16),
                "Чужая станция вдали не тянет духа через полмира: дальше дальности угол — сам дух, иначе дух идёт "
                        + "к чужому верстаку через полкарты. При смене проверки дальности в anchor проверь границу 16");
    }

    @Test
    void anchor_NegativeRange_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> WorkCorner.anchor(new BlockCoord(0, 0, 0), Optional.empty(), -1),
                "Отрицательная дальность обязана отвергаться: по ней ни одна станция не свой угол, иначе угол "
                        + "съезжает и крафт идёт не там. При смене проверки в WorkCorner.anchor проверь границы "
                        + "ноль и минус один");
    }
}
