package com.theminespirit.core.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class OrderBookTests {
    @Test
    void shortage_TwoWaitingOrders_SumsMissing() {
        OrderBook book = new OrderBook();
        book.place("p1", "Анна", "minecraft:torch", "torch", 1, Map.of("coal", 1, "oak_log", 1));
        book.place("p2", "Борис", "minecraft:torch", "torch", 2, Map.of("coal", 2));

        assertEquals(
                Map.of("coal", 3, "oak_log", 1),
                book.shortage(),
                "Недостача очереди — сумма по ждущим заказам: по ней граф ставит задания добычи");
    }

    @Test
    void cancel_ForeignOrder_Refused() {
        OrderBook book = new OrderBook();
        int id = book.place("p1", "Анна", "minecraft:torch", "torch", 1, Map.of("coal", 1))
                .orElseThrow()
                .id();

        assertFalse(book.cancel(id, "p2"), "Чужой заказ отменить нельзя: отменяет только заказчик");
        assertTrue(book.cancel(id, "p1"), "Свой ждущий заказ отменяется");
        assertEquals(Map.of(), book.shortage(), "Отменённый заказ не держит задания духов");
    }

    @Test
    void finish_DoneOrder_LeavesShortageAndKeepsPlace() {
        OrderBook book = new OrderBook();
        int id = book.place("p1", "Анна", "minecraft:torch", "torch", 1, Map.of("coal", 1))
                .orElseThrow()
                .id();

        assertTrue(book.finish(id, "10 64 -3"), "Ждущий заказ завершается");
        assertEquals(OrderBook.Status.DONE, book.all().get(0).status(), "Готовый заказ виден во вкладке со статусом");
        assertEquals("10 64 -3", book.all().get(0).where(), "Готовый заказ помнит, где лежит результат");
        assertFalse(book.finish(id, "x"), "Готовый заказ второй раз не завершается: результат не удваивается");
    }

    @Test
    void place_OverPlayerLimit_Refused() {
        OrderBook book = new OrderBook();
        for (int index = 0; index < OrderBook.MAX_WAITING_PER_PLAYER; index++) {
            book.place("p1", "Анна", "minecraft:torch", "torch", 1, Map.of());
        }

        assertTrue(
                book.place("p1", "Анна", "minecraft:torch", "torch", 1, Map.of())
                        .isEmpty(),
                "Больше " + OrderBook.MAX_WAITING_PER_PLAYER + " ждущих заказов на игрока нельзя: очередь не "
                        + "забивает доску духов");
    }

    @Test
    void of_SavedOrders_ContinuesIds() {
        OrderBook book = new OrderBook();
        book.place("p1", "Анна", "minecraft:torch", "torch", 1, Map.of());

        OrderBook restored = OrderBook.of(book.all(), book.nextId());

        assertEquals(
                2,
                restored.place("p1", "Анна", "minecraft:torch", "torch", 1, Map.of())
                        .orElseThrow()
                        .id(),
                "После перезапуска номера заказов продолжаются: иначе отмена попала бы в чужой заказ");
    }
}
