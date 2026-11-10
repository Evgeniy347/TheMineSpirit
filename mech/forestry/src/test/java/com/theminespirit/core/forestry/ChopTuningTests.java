package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ChopTuningTests {
    @Test
    void gatherDistance_FarRequest_IsCappedToSettlementRadius() {
        assertEquals(
                ChopTuning.GATHER_RADIUS_MAX,
                ChopTuning.gatherDistance(256),
                "Сбор не уходит за радиус поселения: иначе дух идёт к брёвнам за 100+ блоков (observe19 E)");
    }

    @Test
    void gatherDistance_NearRequest_StaysAsRequested() {
        assertEquals(16, ChopTuning.gatherDistance(16), "Ближний сбор не расширяется без нужды");
    }

    @Test
    void gatherDistance_NonPositive_Throws() {
        assertThrows(IllegalArgumentException.class, () -> ChopTuning.gatherDistance(0), "Радиус 0 бессмыслен");
    }

    @Test
    void chopDistance_RequestedHundred_IsNotCutToGatherRadius() {
        int requested = 100;

        assertEquals(
                requested,
                ChopTuning.maxDistance(requested),
                "Рубка ищет дерево в запрошенном радиусе доски (chopRadius=100): срез до 32 оставлял "
                        + "ближайшее дерево в 35–60 блоках недоступным");
        assertNotEquals(
                ChopTuning.gatherDistance(requested),
                ChopTuning.maxDistance(requested),
                "Радиус сбора у поселения (32) и радиус рубки (100) — разные пределы: применение сбора к рубке "
                        + "и дало «ближайшее дерево 35 при пределе 32» (живой случай 11.11)");
    }

    @Test
    void extendTo_BeyondSettlementRadius_IsNotCutBackToGatherRadius() {
        int extended = ChopTuning.extendTo(ChopTuning.GATHER_RADIUS_MAX, 35);

        assertEquals(
                35,
                extended,
                "Расширение поиска деревьев не режется радиусом сбора 32: иначе extend возвращает 32, поиск "
                        + "снова видит 32 и повторяет «ближайшее дерево 35 при пределе 32» (живой случай 11.11)");
        assertEquals(
                extended,
                ChopTuning.scanRadius(ChopTuning.GATHER_RADIUS_MAX, extended),
                "Скан обязан идти до расширенного предела, а не оставаться на 32");
    }

    @Test
    void scanRadius_ExtendedDistance_WidensTheScannedArea() {
        assertEquals(
                35,
                ChopTuning.scanRadius(32, ChopTuning.extendTo(32, 35)),
                "Дерево в 35 блоках при пределе 32 обязано попасть в скан: иначе extend меняет только фильтр, "
                        + "поиск снова видит 32 и повторяет «ближайшее дерево 35 при пределе 32» (живой случай 11.11)");
    }

    @Test
    void scanRadius_WithinLimit_StaysAsRequested() {
        assertEquals(32, ChopTuning.scanRadius(32, 32), "Обычный поиск не расширяется без нужды");
    }

    @Test
    void scanRadius_HugeDistance_IsCappedToMaximumSearch() {
        assertEquals(
                ChopTuning.MAX_SEARCH_RADIUS,
                ChopTuning.scanRadius(32, 10_000),
                "Скан не уходит за предел поиска леса: иначе один дух грузит чанки на километры");
    }
}
