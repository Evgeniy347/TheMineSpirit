package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class ResourceGateTests {

    @Test
    void gate_EnoughOfEverything_Ready() {
        StockNeed need = new StockNeed(Map.of("dirt", 9, "oak_fence", 4));
        StockView stock = new StockView(Map.of("dirt", 9, "oak_fence", 10));

        assertEquals(
                GateOutcome.READY,
                ResourceGate.gate(need, stock),
                "Когда хватает всего, гейт обязан пропустить задание: иначе дух ждёт "
                        + "ресурсы, которые уже лежат в инвентаре");
    }

    @Test
    void gate_MissingOneItem_WaitingAndMissingNamesItem() {
        StockNeed need = new StockNeed(Map.of("dirt", 9, "torch", 4));
        StockView stock = new StockView(Map.of("dirt", 9));

        assertEquals(
                GateOutcome.WAITING,
                ResourceGate.gate(need, stock),
                "Нехватка одного предмета обяна держать задание в очереди: взяв задание "
                        + "без факелов, дух встанет перед тёмной стеной");
        assertEquals(
                Map.of("torch", 4),
                ResourceGate.missing(need, stock),
                "Отчёт нехватки обязан называть предмет и число: наблюдетель и стратег "
                        + "видят, что добывать, до постановки задания");
    }

    @Test
    void gate_EmptyNeed_Ready() {
        StockNeed need = new StockNeed(Map.of());
        StockView stock = new StockView(Map.of());

        assertEquals(
                GateOutcome.READY,
                ResourceGate.gate(need, stock),
                "Задание без ресурсов обязано пройти гейт без ожидания: ждать нечего, "
                        + "зависшее задание блокировало бы всю очередь поселения");
    }

    @Test
    void gate_ExactAmount_Ready() {
        StockNeed need = new StockNeed(Map.of("dirt", 9));
        StockView stock = new StockView(Map.of("dirt", 9));

        assertEquals(
                GateOutcome.READY,
                ResourceGate.gate(need, stock),
                "Ровно столько, сколько нужно, — готово: последний блок земли принадлежит заданию");
    }

    @Test
    void gate_Surplus_Ready() {
        StockNeed need = new StockNeed(Map.of("dirt", 9));
        StockView stock = new StockView(Map.of("dirt", 64));

        assertEquals(
                GateOutcome.READY,
                ResourceGate.gate(need, stock),
                "Избыток в запасах обязан пройти гейт: лишние ресурсы принадлежат другим "
                        + "заданиям, гейт считает только нужду");
    }

    @Test
    void gate_ZeroInStock_Waiting() {
        StockNeed need = new StockNeed(Map.of("dirt", 9));
        StockView stock = new StockView(Map.of("dirt", 0));

        assertEquals(
                GateOutcome.WAITING,
                ResourceGate.gate(need, stock),
                "Ноль в запасе при нужде обязан держать задание: доска сначала ставит добычу "
                        + "недостающего, а не отдаёт задание в пустые руки");
        assertEquals(
                Map.of("dirt", 9),
                ResourceGate.missing(need, stock),
                "Нехватка при нулевом запасе равна всей нужде: добыча обяна закрыть всё");
    }

    @Test
    void missing_PositiveShortages_Only() {
        StockNeed need = new StockNeed(Map.of("dirt", 9, "torch", 4));
        StockView stock = new StockView(Map.of("dirt", 3, "torch", 10));

        assertEquals(
                Map.of("dirt", 6),
                ResourceGate.missing(need, stock),
                "В отчёт нехватки идут только положительные числа: избыток факелов не "
                        + "превращается в отрицательную нехватку, наблюдетель видит чистый остаток");
    }

    @Test
    void gate_NullArguments_Rejected() {
        assertThrows(
                NullPointerException.class,
                () -> ResourceGate.gate(null, new StockView(Map.of())),
                "need=null обязан отвергаться: гейт без нужды не проверить");
        assertThrows(
                NullPointerException.class,
                () -> ResourceGate.gate(new StockNeed(Map.of()), null),
                "stock=null обязан отвергаться: гейт без снимка запасов не проверить");
    }

    @Test
    void gate_Is_Deterministic() {
        StockNeed need = new StockNeed(Map.of("dirt", 9, "torch", 4));
        StockView stock = new StockView(Map.of("dirt", 9));

        assertEquals(
                ResourceGate.gate(need, stock),
                ResourceGate.gate(need, stock),
                "Гейт обязан быть детерминирован: по нему доска держит задание в очереди, "
                        + "нестабильный вердикт перетасует выдачу между пересборками");
    }

    @Property
    void gate_Monotone_ByStock(
            @ForAll("stockNeeds") StockNeed need,
            @ForAll("stockViews") StockView stock,
            @ForAll("stockNeeds") StockNeed extra) {
        Map<String, Integer> grown = new HashMap<>(stock.available());
        extra.items().forEach((item, count) -> grown.merge(item, count, Integer::sum));
        StockView grownStock = new StockView(grown);

        if (ResourceGate.gate(need, stock) == GateOutcome.READY) {
            assertEquals(
                    GateOutcome.READY,
                    ResourceGate.gate(need, grownStock),
                    "Добавление ресурса не обязано переводить готовое задание в ожидание: "
                            + "монотонность гейта — иначе пополнение склада зависит ждать, а очередь не двигается");
        }
        assertTrue(
                ResourceGate.missing(need, grownStock).entrySet().stream()
                        .allMatch(entry ->
                                ResourceGate.missing(need, stock).getOrDefault(entry.getKey(), 0) >= entry.getValue()),
                "Нехватка обязана убывать при пополнении: иначе отчёт наблюдателя врёт о добыче");
    }

    @Property
    void missing_PositiveShortages_Only(@ForAll("stockNeeds") StockNeed need, @ForAll("stockViews") StockView stock) {
        Map<String, Integer> missing = ResourceGate.missing(need, stock);

        for (Map.Entry<String, Integer> entry : missing.entrySet()) {
            assertTrue(
                    entry.getValue() > 0,
                    "Отчёт нехватки обязан содержать только положительные числа: " + entry.getKey() + "="
                            + entry.getValue()
                            + " — отрицательная нехватка означала бы избыток, которого нет в отчёте");
        }
    }

    @Provide
    Arbitrary<StockNeed> stockNeeds() {
        return Arbitraries.of(
                new StockNeed(Map.of()),
                new StockNeed(Map.of("dirt", 1)),
                new StockNeed(Map.of("dirt", 9, "torch", 4)),
                new StockNeed(Map.of("oak_fence", 16, "cobblestone", 64, "wheat", 2)));
    }

    @Provide
    Arbitrary<StockView> stockViews() {
        return Arbitraries.of(
                new StockView(Map.of()),
                new StockView(Map.of("dirt", 0)),
                new StockView(Map.of("dirt", 9, "torch", 4)),
                new StockView(Map.of("oak_fence", 100, "cobblestone", 128, "wheat", 8)));
    }

    @Test
    void missing_ActionPlacedDirtPath_DoesNotWaitForStock() {
        StockNeed need = new StockNeed(Map.of("dirt_path", 20, "torch", 4));
        StockView stock = new StockView(Map.of());

        Map<String, Integer> missing = ResourceGate.missing(need, stock);

        assertFalse(
                missing.containsKey("dirt_path"),
                "Дорожка сада делается лопатой по земле, а не лежит на складе: гейт не должен ждать её как предмет. "
                        + "Нехватка: " + missing);
        assertEquals(
                4,
                missing.get("torch"),
                "Факелы сада — обычный материал: их нехватка обязана остаться гейту. Нехватка: " + missing);
    }
}
