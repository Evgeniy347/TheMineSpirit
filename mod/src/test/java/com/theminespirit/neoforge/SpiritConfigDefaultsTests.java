package com.theminespirit.neoforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SpiritConfigDefaultsTests {
    @Test
    void defaults_SearchRadiusAndStoreCaps_ArePositiveAndOrdered() {
        assertEquals(64, SpiritConfig.DEFAULT_SEARCH_Y_RADIUS, "радиус поиска по Y по умолчанию — 64");
        assertTrue(
                SpiritConfig.DEFAULT_MORE_DROPS_MAX < SpiritConfig.DEFAULT_MORE_DANGERS_MAX,
                "дропы теряются быстрее опасностей: книга добычи меньше книги опасностей");
        assertEquals(
                SpiritConfig.DEFAULT_MORE_BROKEN_MAX,
                SpiritConfig.DEFAULT_MORE_DANGERS_MAX,
                "книги сноса и опасностей одного размера: обе помнят следы правок мира");
        assertEquals(64, SpiritConfig.DEFAULT_MORE_COUNT_MAX, "предел пачки предметов — 64, как стак");
        assertEquals(4, SpiritConfig.DEFAULT_COLLECT_RADIUS, "обычный сбор дропа — 4 блока");
        assertTrue(
                SpiritConfig.DEFAULT_TIGHT_COLLECT_RADIUS < SpiritConfig.DEFAULT_COLLECT_RADIUS,
                "мелкие работы собирают ближе: рядом чужие вещи");
    }
}
