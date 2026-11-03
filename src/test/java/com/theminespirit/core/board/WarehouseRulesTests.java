package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class WarehouseRulesTests {
    @Test
    void site_EachLevel_StaysAtHomeAnchor() {
        BlockCoord home = new BlockCoord(10, 64, -5);

        assertEquals(home, WarehouseRules.site(home, 1), "Первый склад ставится у дома");
        assertEquals(
                home,
                WarehouseRules.site(home, 2),
                "Второй склад улучшает первый на месте: второй участок не занимается");
        assertThrows(
                IllegalArgumentException.class, () -> WarehouseRules.site(home, 0), "Места для нулевого уровня нет");
    }

    @Test
    void levelOf_NodeWithLevel_ReturnsItsLevel() {
        assertEquals(1, WarehouseRules.levelOf("warehouse"), "Узел building:warehouse — первый уровень");
        assertEquals(3, WarehouseRules.levelOf("warehouse_3"), "Узел warehouse_3 — третий уровень");
    }

    @Test
    void scheme_EachLevel_NamesItsOwnPreset() {
        assertEquals("storage_v1", WarehouseRules.scheme(1), "Уровень 1 — схема storage_v1");
        assertEquals("storage_v4", WarehouseRules.scheme(4), "Уровень 4 — схема storage_v4");
        assertThrows(IllegalArgumentException.class, () -> WarehouseRules.scheme(5), "Схемы пятого уровня нет");
    }
}
