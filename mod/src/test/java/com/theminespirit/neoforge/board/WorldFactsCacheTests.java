package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WorldFactsCacheTests {
    @Test
    void sameKey_WithinTtl_ReusesFacts() {
        assertTrue(
                WorldFactsCache.fresh(true, WorldFactsCache.TTL_TICKS - 1),
                "аудит построек и факты участка не пересчитываются на каждой пересборке доски (сервер 12.11:"
                        + " factWorld ≈0,35 с каждые 2,4 с)");
    }

    @Test
    void cache_ExpiredOrChangedKey_Recomputes() {
        assertFalse(WorldFactsCache.fresh(true, WorldFactsCache.TTL_TICKS), "старше TTL — пересчёт");
        assertFalse(WorldFactsCache.fresh(false, 0), "сменилась постройка или место этапа — пересчёт сразу");
        assertFalse(WorldFactsCache.fresh(true, -1), "время мира пошло назад (откат) — пересчёт");
    }
}
