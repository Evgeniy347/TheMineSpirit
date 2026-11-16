package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChestOpenRuleTests {
    @Test
    void singleChest_CoveredAbove_IsBlocked() {
        assertTrue(
                ChestOpenRule.blocked(List.of(true)),
                "одиночный сундук под твёрдым блоком не открыть: нужна расчистка");
    }

    @Test
    void doubleChest_OneHalfFree_IsOpenable() {
        assertFalse(
                ChestOpenRule.blocked(List.of(true, false)),
                "двойной сундук открывается со свободной половины: сносить крышку над занятой не нужно");
    }

    @Test
    void doubleChest_BothHalvesCovered_IsBlocked() {
        assertTrue(
                ChestOpenRule.blocked(List.of(true, true)),
                "обе половины двойного сундука накрыты — открыть нечем: обе крышки на снос");
    }

    @Test
    void chestAboveChest_CoveredHalf_IsOpenable() {
        assertFalse(
                ChestOpenRule.blocked(List.of(false)),
                "сундук над сундуком открытию не мешает: крышку считать преградой нельзя");
    }
}
