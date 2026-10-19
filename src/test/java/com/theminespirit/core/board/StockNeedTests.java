package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StockNeedTests {

    @Test
    void need_NullMap_Rejected() {
        assertThrows(
                NullPointerException.class,
                () -> new StockNeed(null),
                "Карта нужды=null обязана отвергаться: задание без ресурсов задают пустой картой");
    }

    @Test
    void need_NegativeQuantity_Rejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new StockNeed(Map.of("dirt", -3)),
                "Отрицательная нужда обязана отвергаться с текстом: отрицательное количество "
                        + "сломало бы гейт — нехватка стала бы отрицательной и задание прошло мимо запасов");
        assertTrue(
                error.getMessage().contains("dirt"),
                "Сообщение обязано называть предмет: фактическое " + error.getMessage());
    }

    @Test
    void need_NullQuantity_Rejected() {
        Map<String, Integer> items = new HashMap<>();
        items.put("dirt", null);

        assertThrows(
                IllegalArgumentException.class,
                () -> new StockNeed(items),
                "Нужда с null-количеством обязана отвергаться: null не отличить от нуля, " + "а гейт сравнивает числа");
    }

    @Test
    void need_EmptyMeans_NoResourcesRequired() {
        StockNeed need = new StockNeed(Map.of());

        assertEquals(
                Map.of(), need.items(), "Пустая нужда означает «ресурсов не требуется»: такое задание не ждёт гейта");
    }
}
