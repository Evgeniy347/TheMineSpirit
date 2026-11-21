package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.crafting.OrderBook;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class More7RulesPart2Tests {
    private static final SupplyGraph GRAPH = SupplyGraph.loadDefault();

    private static OrderBook.Order order(int id, OrderBook.Status status, Map<String, Integer> missing) {
        return new OrderBook.Order(id, "player", "Игрок", "recipe", "stone", 1, status, missing, "");
    }

    @Test
    void gated_NextStageAndLaterBuilding_Flagged() {
        Set<String> gated =
                More7Rules.gated(GRAPH, 1, List.of("iron_ingot", "raw_iron", "stone", "log", "#logs", "unknown"));
        assertTrue(
                gated.contains("iron_ingot"),
                "Железо без этапа 2 недостижимо — цель не ставится раньше Иначе тест gated_NextStageAndLaterBuilding_Flagged перестанет ловить эту поломку. При смене gated_NextStageAndLaterBuilding_Flagged проверь это утверждение.");
        assertTrue(
                gated.contains("raw_iron"),
                "Руда этапа 2 тоже ждёт следующего этапа Иначе тест gated_NextStageAndLaterBuilding_Flagged перестанет ловить эту поломку. При смене gated_NextStageAndLaterBuilding_Flagged проверь это утверждение.");
        assertFalse(
                gated.contains("stone"),
                "Камень доступен на первом этапе Иначе тест gated_NextStageAndLaterBuilding_Flagged перестанет ловить эту поломку. При смене gated_NextStageAndLaterBuilding_Flagged проверь это утверждение.");
        assertFalse(
                gated.contains("#logs"),
                "Группа предметов не является целью этапа Иначе тест gated_NextStageAndLaterBuilding_Flagged перестанет ловить эту поломку. При смене gated_NextStageAndLaterBuilding_Flagged проверь это утверждение.");
        assertFalse(
                gated.contains("unknown"),
                "Неизвестный графу предмет не объявляют закрытым этапом Иначе тест gated_NextStageAndLaterBuilding_Flagged перестанет ловить эту поломку. При смене gated_NextStageAndLaterBuilding_Flagged проверь это утверждение.");
        assertFalse(
                More7Rules.gated(GRAPH, 2, List.of("iron_ingot")).contains("iron_ingot"),
                "На втором этапе железо уже достижимо Иначе тест gated_NextStageAndLaterBuilding_Flagged перестанет ловить эту поломку. При смене gated_NextStageAndLaterBuilding_Flagged проверь это утверждение.");
        assertTrue(
                More7Rules.gated(GRAPH, 2, List.of("bed")).contains("bed"),
                "Кровать этапа 3 не ставят на втором этапе Иначе тест gated_NextStageAndLaterBuilding_Flagged перестанет ловить эту поломку. При смене gated_NextStageAndLaterBuilding_Flagged проверь это утверждение.");
    }

    @Test
    void gated_IronToolBeforeItsStage_Flagged() {
        assertTrue(
                More7Rules.gated(GRAPH, 1, List.of("iron_pickaxe")).contains("iron_pickaxe"),
                "Железный инструмент требует этапа 2 Иначе тест gated_IronToolBeforeItsStage_Flagged перестанет ловить эту поломку. При смене gated_IronToolBeforeItsStage_Flagged проверь это утверждение.");
        assertFalse(
                More7Rules.gated(GRAPH, 2, List.of("iron_pickaxe")).contains("iron_pickaxe"),
                "На втором этапе железный инструмент — штатная цель Иначе тест gated_IronToolBeforeItsStage_Flagged перестанет ловить эту поломку. При смене gated_IronToolBeforeItsStage_Flagged проверь это утверждение.");
        assertFalse(
                More7Rules.gated(GRAPH, 1, List.of("stone_pickaxe")).contains("stone_pickaxe"),
                "Каменный инструмент доступен на первом этапе Иначе тест gated_IronToolBeforeItsStage_Flagged перестанет ловить эту поломку. При смене gated_IronToolBeforeItsStage_Flagged проверь это утверждение.");
    }

    @Test
    void needsLater_StageBelowOne_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> More7Rules.needsLater(GRAPH, 0, "stone"),
                "Нулевого этапа нет, спрашивать не у чего Иначе тест needsLater_StageBelowOne_Rejected перестанет ловить эту поломку. При смене needsLater_StageBelowOne_Rejected проверь это утверждение.");
    }

    @Test
    void liveOrders_GatedItems_RemovedUntilTheirStage() {
        Map<String, Integer> orders =
                More7Rules.liveOrders(GRAPH, 1, Map.of("iron_ingot", 5, "iron_pickaxe", 1, "stone", 2));
        assertFalse(
                orders.containsKey("iron_ingot"),
                "Заказ на железо не ставится раньше второго этапа Иначе тест liveOrders_GatedItems_RemovedUntilTheirStage перестанет ловить эту поломку. При смене liveOrders_GatedItems_RemovedUntilTheirStage проверь это утверждение.");
        assertFalse(
                orders.containsKey("iron_pickaxe"),
                "Заказ на железный инструмент тоже ждёт этапа Иначе тест liveOrders_GatedItems_RemovedUntilTheirStage перестанет ловить эту поломку. При смене liveOrders_GatedItems_RemovedUntilTheirStage проверь это утверждение.");
        assertEquals(
                Map.of("stone", 2),
                orders,
                "Доступный заказ остаётся как был Иначе тест liveOrders_GatedItems_RemovedUntilTheirStage перестанет ловить эту поломку. При смене liveOrders_GatedItems_RemovedUntilTheirStage проверь это утверждение.");
        assertEquals(
                Map.of("iron_ingot", 5),
                More7Rules.liveOrders(GRAPH, 2, Map.of("iron_ingot", 5)),
                "На своём этапе железо снова становится целью Иначе тест liveOrders_GatedItems_RemovedUntilTheirStage перестанет ловить эту поломку. При смене liveOrders_GatedItems_RemovedUntilTheirStage проверь это утверждение.");
    }

    @Test
    void merge_AddsEventsAndOrders_WithoutLosingBase() {
        GraphState state = new GraphState(
                Map.of("stone", 8),
                1,
                Map.of(),
                Map.of(),
                Map.of(),
                100,
                Set.of("night"),
                Set.of(),
                Map.of(),
                Map.of(),
                Map.of("stone", 2),
                GraphFacts.empty());
        GraphState next = More7Rules.merge(state, Set.of("more7_work_pileup", "more7_order_ready"), Map.of("stone", 1));
        assertTrue(
                next.events().contains("night"),
                "Сборщик не теряет события других сборщиков Иначе тест merge_AddsEventsAndOrders_WithoutLosingBase перестанет ловить эту поломку. При смене merge_AddsEventsAndOrders_WithoutLosingBase проверь это утверждение.");
        assertTrue(
                next.events().contains("more7_work_pileup"),
                "Занятость духов добавляется к событиям Иначе тест merge_AddsEventsAndOrders_WithoutLosingBase перестанет ловить эту поломку. При смене merge_AddsEventsAndOrders_WithoutLosingBase проверь это утверждение.");
        assertTrue(
                next.events().contains("more7_order_ready"),
                "Готовый заказ добавляется к событиям Иначе тест merge_AddsEventsAndOrders_WithoutLosingBase перестанет ловить эту поломку. При смене merge_AddsEventsAndOrders_WithoutLosingBase проверь это утверждение.");
        assertEquals(
                Map.of("stone", 1),
                next.orders(),
                "Очередь заказов заменяется отфильтрованной Иначе тест merge_AddsEventsAndOrders_WithoutLosingBase перестанет ловить эту поломку. При смене merge_AddsEventsAndOrders_WithoutLosingBase проверь это утверждение.");
        assertEquals(
                state.facts(),
                next.facts(),
                "Сборщик не подменяет факты графа Иначе тест merge_AddsEventsAndOrders_WithoutLosingBase перестанет ловить эту поломку. При смене merge_AddsEventsAndOrders_WithoutLosingBase проверь это утверждение.");
        assertEquals(
                state.stock(),
                next.stock(),
                "Сборщик не подменяет запас Иначе тест merge_AddsEventsAndOrders_WithoutLosingBase перестанет ловить эту поломку. При смене merge_AddsEventsAndOrders_WithoutLosingBase проверь это утверждение.");
    }

    @Test
    void readyOrders_OnlyWaitingWithoutMissing_Counted() {
        List<OrderBook.Order> orders = List.of(
                order(1, OrderBook.Status.WAITING, Map.of()),
                order(2, OrderBook.Status.WAITING, Map.of("stick", 2)),
                order(3, OrderBook.Status.DONE, Map.of()),
                order(4, OrderBook.Status.CANCELLED, Map.of()));
        assertEquals(
                1,
                More7Rules.readyOrders(orders),
                "Готов к сдаче только ожидающий заказ без недостачи Иначе тест readyOrders_OnlyWaitingWithoutMissing_Counted перестанет ловить эту поломку. При смене readyOrders_OnlyWaitingWithoutMissing_Counted проверь это утверждение.");
        assertEquals(
                0,
                More7Rules.readyOrders(List.of()),
                "Без заказов сдавать нечего Иначе тест readyOrders_OnlyWaitingWithoutMissing_Counted перестанет ловить эту поломку. При смене readyOrders_OnlyWaitingWithoutMissing_Counted проверь это утверждение.");
    }

    @Test
    void events_AllSignals_AddExpected() {
        More7Rules.Signals signals = new More7Rules.Signals(
                new More7Rules.Workload(2, 2, 1),
                1,
                new More7Rules.Day(true, true, 1, 1),
                new More7Rules.Capacity(10, 10),
                new More7Rules.Capacity(8, 8),
                1);
        Set<String> events = More7Rules.events(signals);
        assertTrue(
                events.containsAll(Set.of(
                        More7Rules.WORK_PILEUP,
                        More7Rules.GATED_ITEM,
                        More7Rules.MORNING_HARVEST,
                        More7Rules.EVENING_RETURN,
                        More7Rules.PEN_FULL,
                        More7Rules.FIELD_FULL,
                        More7Rules.ORDER_READY)),
                "Все входы области дают свои события:  Иначе тест events_AllSignals_AddExpected перестанет ловить эту поломку. При смене events_AllSignals_AddExpected проверь это утверждение."
                        + events);
    }

    @Test
    void events_EmptySignals_AddNothing() {
        More7Rules.Signals signals = new More7Rules.Signals(
                new More7Rules.Workload(0, 0, 0),
                0,
                new More7Rules.Day(false, false, 0, 0),
                new More7Rules.Capacity(0, 0),
                new More7Rules.Capacity(0, 0),
                0);
        assertEquals(
                Set.of(),
                More7Rules.events(signals),
                "Пустой вход не ставит работ Иначе тест events_EmptySignals_AddNothing перестанет ловить эту поломку. При смене events_EmptySignals_AddNothing проверь это утверждение.");
    }

    @Test
    void events_MorningWithoutHarvestAndEveningAtHome_AddNothing() {
        More7Rules.Signals signals = new More7Rules.Signals(
                new More7Rules.Workload(0, 0, 0),
                0,
                new More7Rules.Day(true, true, 0, 0),
                new More7Rules.Capacity(0, 0),
                new More7Rules.Capacity(0, 0),
                0);
        assertEquals(
                Set.of(),
                More7Rules.events(signals),
                "Пик без нужды не меняет очередь Иначе тест events_MorningWithoutHarvestAndEveningAtHome_AddNothing перестанет ловить эту поломку. При смене events_MorningWithoutHarvestAndEveningAtHome_AddNothing проверь это утверждение.");
    }

    @Test
    void signals_NegativeCounters_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new More7Rules.Signals(
                        new More7Rules.Workload(0, 0, 0),
                        -1,
                        new More7Rules.Day(false, false, 0, 0),
                        new More7Rules.Capacity(0, 0),
                        new More7Rules.Capacity(0, 0),
                        0),
                "Отрицательное число закрытых предметов не имеет смысла Иначе тест signals_NegativeCounters_Rejected перестанет ловить эту поломку. При смене signals_NegativeCounters_Rejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> new More7Rules.Signals(
                        new More7Rules.Workload(0, 0, 0),
                        0,
                        new More7Rules.Day(false, false, -1, 0),
                        new More7Rules.Capacity(0, 0),
                        new More7Rules.Capacity(0, 0),
                        0),
                "Отрицательный урожай не имеет смысла Иначе тест signals_NegativeCounters_Rejected перестанет ловить эту поломку. При смене signals_NegativeCounters_Rejected проверь это утверждение.");
    }
}
