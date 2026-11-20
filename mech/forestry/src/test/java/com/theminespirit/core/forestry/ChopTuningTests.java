package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.forestry.api.ChopTuning;
import org.junit.jupiter.api.Test;

class ChopTuningTests {
    @Test
    void gatherDistance_FarRequest_IsCappedToSettlementRadius() {
        assertEquals(
                ChopTuning.GATHER_RADIUS_MAX,
                ChopTuning.gatherDistance(256),
                "Сбор не уходит за радиус поселения, иначе дух идёт к брёвнам за 100+ блоков (observe19 E). При "
                        + "смене ChopTuning.gatherDistance проверь строку ближнего ниже в тесте");
    }

    @Test
    void gatherDistance_NearRequest_StaysAsRequested() {
        assertEquals(
                16,
                ChopTuning.gatherDistance(16),
                "Ближний сбор не расширяется без нужды, иначе дух ходит дальше заказа и возвращается пустым. При "
                        + "смене gatherDistance проверь строку нуля ниже в тесте");
    }

    @Test
    void gatherDistance_NonPositive_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ChopTuning.gatherDistance(0),
                "Радиус 0 бессмыслен, иначе сбор не имеет области и дух стоит на месте. При смене проверки в "
                        + "gatherDistance проверь строку 256 выше в тесте");
    }

    @Test
    void chopDistance_RequestedHundred_IsNotCutToGatherRadius() {
        int requested = 100;

        assertEquals(
                requested,
                ChopTuning.maxDistance(requested),
                "Рубка ищет дерево в запрошенном радиусе доски (chopRadius=100): срез до 32 оставлял ближайшее "
                        + "дерево в 35–60 блоках недоступным, иначе рубка не находит лес. При смене maxDistance "
                        + "проверь строку ниже в тесте");
        assertNotEquals(
                ChopTuning.gatherDistance(requested),
                ChopTuning.maxDistance(requested),
                "Радиус сбора у поселения (32) и радиус рубки (100) — разные пределы: применение сбора к рубке и "
                        + "дало «ближайшее дерево 35 при пределе 32» (живой случай 11.11), иначе пределы путаются. "
                        + "При смене констант проверь строку выше в тесте");
    }

    @Test
    void extendTo_BeyondSettlementRadius_IsNotCutBackToGatherRadius() {
        int extended = ChopTuning.extendTo(ChopTuning.GATHER_RADIUS_MAX, 35);

        assertEquals(
                35,
                extended,
                "Расширение поиска деревьев не режется радиусом сбора 32: иначе extend возвращает 32, поиск снова "
                        + "видит 32 и повторяет «ближайшее дерево 35 при пределе 32» (живой случай 11.11). При смене "
                        + "extendTo проверь строку скана ниже в тесте");
        assertEquals(
                extended,
                ChopTuning.scanRadius(ChopTuning.GATHER_RADIUS_MAX, extended),
                "Скан обязан идти до расширенного предела, а не оставаться на 32, иначе расширение бесполезно и "
                        + "дерево снова не находится. При смене scanRadius проверь строку выше в тесте");
    }

    @Test
    void extendTo_NoKnownTree_WidensSearchAnyway() {
        int widened = ChopTuning.extendTo(ChopTuning.GATHER_RADIUS_MAX, -1);

        assertEquals(
                64,
                widened,
                "Деревьев в радиусе 32 не видно — известного дальнего дерева нет, и поиск обязан расшириться, иначе "
                        + "extendTo возвращает 32 и рубка навсегда падает «деревья породы oak в радиусе 32 "
                        + "закончились» (355 провалов GATHER_MATERIALS за 30 дней). При смене ветки -1 проверь "
                        + "строку на пределе ниже в тесте");
    }

    @Test
    void extendTo_TreeExactlyAtLimit_WidensSearch() {
        assertEquals(
                64,
                ChopTuning.extendTo(32, 32),
                "Дерево ровно на пределе 32 не найдено прежним сканом — расширение обязано дойти до 64, иначе "
                        + "предел остаётся и дерево теряется. При смене extendTo проверь строку максимума ниже в "
                        + "тесте");
    }

    @Test
    void extendTo_NoRoomToWiden_StaysAtMaximum() {
        assertEquals(
                ChopTuning.MAX_SEARCH_RADIUS,
                ChopTuning.extendTo(ChopTuning.MAX_SEARCH_RADIUS, -1),
                "На пределе поиска расширяться некуда: скан не уходит дальше MAX_SEARCH_RADIUS, иначе дух грузит "
                        + "чанки. При смене MAX_SEARCH_RADIUS проверь строку выше в тесте");
    }

    @Test
    void scanRadius_ExtendedDistance_WidensTheScannedArea() {
        assertEquals(
                35,
                ChopTuning.scanRadius(32, ChopTuning.extendTo(32, 35)),
                "Дерево в 35 блоках при пределе 32 обязано попасть в скан: иначе extend меняет только фильтр, "
                        + "поиск снова видит 32 и повторяет «ближайшее дерево 35 при пределе 32» (живой случай "
                        + "11.11). При смене scanRadius проверь строку в пределе ниже в тесте");
    }

    @Test
    void scanRadius_WithinLimit_StaysAsRequested() {
        assertEquals(
                32,
                ChopTuning.scanRadius(32, 32),
                "Обычный поиск не расширяется без нужды, иначе скан грузит лишние чанки. При смене scanRadius "
                        + "проверь строку огромного ниже в тесте");
    }

    @Test
    void scanRadius_HugeDistance_IsCappedToMaximumSearch() {
        assertEquals(
                ChopTuning.MAX_SEARCH_RADIUS,
                ChopTuning.scanRadius(32, 10_000),
                "Скан не уходит за предел поиска леса, иначе один дух грузит чанки на километры. При смене предела "
                        + "в scanRadius проверь строку в пределе выше в тесте");
    }
}
