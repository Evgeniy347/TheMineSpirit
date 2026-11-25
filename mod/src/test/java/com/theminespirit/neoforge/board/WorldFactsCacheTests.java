package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.Map;
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
    void buildingRevision_SameStates_ReusesRevision() {
        Map<String, Built> states = Map.of("hut", Built.BUILT, "mine", Built.PLANNED);
        assertEquals(
                7,
                BuildingStates.revision(states, Map.of("mine", Built.PLANNED, "hut", Built.BUILT), 7),
                "Повторный аудит с теми же постройками обязан сохранить ревизию: иначе WorldFactsCache"
                        + " снова считает факты мира на каждой пересборке и держит серверный поток."
                        + " При смене buildingRevision_SameStates_ReusesRevision проверь WorldFactsCacheTests и живой graph.in.factWorld.");
    }

    @Test
    void buildingRevision_ChangedStates_GrowsRevision() {
        Map<String, Built> before = Map.of("hut", Built.PLANNED, "mine", Built.PLANNED);
        assertEquals(
                8,
                BuildingStates.revision(before, Map.of("hut", Built.BUILT, "mine", Built.PLANNED), 7),
                "Завершённая постройка обязана сменить ревизию: иначе граф продолжит видеть её незавершённой"
                        + " и будет выдавать задачу на уже готовый дом."
                        + " При смене buildingRevision_ChangedStates_GrowsRevision проверь GraphBuildInputs и аудит этапа.");
        assertEquals(
                8,
                BuildingStates.revision(before, Map.of("hut", Built.PLANNED), 7),
                "Исчезнувшая из плана постройка тоже меняет ключ фактов: иначе станция или источник материала"
                        + " останется в графе после удаления места и породит невыполнимую задачу."
                        + " При смене buildingRevision_ChangedStates_GrowsRevision проверь удаление мест плана и GraphFactInputs.");
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
