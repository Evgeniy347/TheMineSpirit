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
                        + " factWorld ≈0,35 с каждые 2,4 с)"
                        + " Иначе тест sameKey_WithinTtl_ReusesFacts перестанет ловить эту поломку. При смене sameKey_WithinTtl_ReusesFacts проверь это утверждение.");
    }

    @Test
    void cache_ExpiredOrChangedKey_Recomputes() {
        assertFalse(
                WorldFactsCache.fresh(true, WorldFactsCache.TTL_TICKS),
                "старше TTL — пересчёт"
                        + " Иначе тест cache_ExpiredOrChangedKey_Recomputes перестанет ловить эту поломку. При смене cache_ExpiredOrChangedKey_Recomputes проверь это утверждение.");
        assertFalse(
                WorldFactsCache.fresh(false, 0),
                "сменилась постройка или место этапа — пересчёт сразу"
                        + " Иначе тест cache_ExpiredOrChangedKey_Recomputes перестанет ловить эту поломку. При смене cache_ExpiredOrChangedKey_Recomputes проверь это утверждение.");
        assertFalse(
                WorldFactsCache.fresh(true, -1),
                "время мира пошло назад (откат) — пересчёт"
                        + " Иначе тест cache_ExpiredOrChangedKey_Recomputes перестанет ловить эту поломку. При смене cache_ExpiredOrChangedKey_Recomputes проверь это утверждение.");
    }
}
