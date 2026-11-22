package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class WarehouseRulesTests {
    @Test
    void site_EachLevel_StaysAtHomeAnchor() {
        BlockCoord home = new BlockCoord(10, 64, -5);

        assertEquals(
                home,
                WarehouseRules.site(home, 1),
                "Первый склад ставится у дома"
                        + " Иначе тест site_EachLevel_StaysAtHomeAnchor перестанет ловить эту поломку. При смене site_EachLevel_StaysAtHomeAnchor проверь это утверждение.");
        assertEquals(
                home,
                WarehouseRules.site(home, 2),
                "Второй склад улучшает первый на месте: второй участок не занимается"
                        + " Иначе тест site_EachLevel_StaysAtHomeAnchor перестанет ловить эту поломку. При смене site_EachLevel_StaysAtHomeAnchor проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> WarehouseRules.site(home, 0),
                "Места для нулевого уровня нет"
                        + " Иначе тест site_EachLevel_StaysAtHomeAnchor перестанет ловить эту поломку. При смене site_EachLevel_StaysAtHomeAnchor проверь это утверждение.");
    }

    @Test
    void scheme_EachLevel_NamesItsOwnPreset() {
        assertEquals(
                "storage_v1",
                WarehouseRules.scheme(1),
                "Уровень 1 — схема storage_v1"
                        + " Иначе тест scheme_EachLevel_NamesItsOwnPreset перестанет ловить эту поломку. При смене scheme_EachLevel_NamesItsOwnPreset проверь это утверждение.");
        assertEquals(
                "storage_v4",
                WarehouseRules.scheme(4),
                "Уровень 4 — схема storage_v4"
                        + " Иначе тест scheme_EachLevel_NamesItsOwnPreset перестанет ловить эту поломку. При смене scheme_EachLevel_NamesItsOwnPreset проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> WarehouseRules.scheme(5),
                "Схемы пятого уровня нет"
                        + " Иначе тест scheme_EachLevel_NamesItsOwnPreset перестанет ловить эту поломку. При смене scheme_EachLevel_NamesItsOwnPreset проверь это утверждение.");
    }
}
