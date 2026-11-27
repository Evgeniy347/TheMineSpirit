package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BoardReserveTests {
    private static BoardTicket ticket(String id, BoardCase boardCase, Map<String, Integer> items) {
        return new BoardTicket(
                id,
                boardCase,
                Optional.empty(),
                BoardRules.priorityOf(boardCase),
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                items);
    }

    @Test
    void items_OpenAndClosedTickets_OnlyOpenReserve() {
        Board board = new Board(() -> 0);
        board.submit(ticket("chests", BoardCase.STOCK_CHESTS, Map.of("jungle_planks", 16)));
        board.submit(ticket("smoker", BoardCase.CRAFT_SMOKER, Map.of("jungle_planks", 4)));
        board.claim("smoker", UUID.randomUUID(), 0);
        board.complete("smoker");

        assertEquals(
                Map.of("jungle_planks", 16),
                BoardReserve.items(board),
                "Бронируют только открытые задания: закрытая коптильня досок не держит"
                        + " Иначе тест items_OpenAndClosedTickets_OnlyOpenReserve перестанет ловить эту поломку. При смене items_OpenAndClosedTickets_OnlyOpenReserve проверь это утверждение.");
    }

    @Test
    void free_StockWithFullIds_SubtractsReservedShortIds() {
        Map<String, Integer> stock = Map.of("minecraft:jungle_planks", 10, "minecraft:stick", 3);

        Map<String, Integer> free = BoardReserve.free(stock, Map.of("jungle_planks", 16, "stick", 1));

        assertEquals(
                Map.of("minecraft:stick", 2),
                free,
                "Бронь в коротких id вычитается из полных id запаса"
                        + " Иначе тест free_StockWithFullIds_SubtractsReservedShortIds перестанет ловить эту поломку. При смене free_StockWithFullIds_SubtractsReservedShortIds проверь это утверждение.");
    }

    @Test
    void take_OnePass_DoesNotSpendSameStickTwice() {
        Map<String, Integer> stock = Map.of("minecraft:stick", 4, "minecraft:cobblestone", 2);
        Map<String, Integer> held = new HashMap<>();

        assertTrue(
                BoardReserve.take(stock, held, Map.of("stick", 2, "cobblestone", 1)),
                "Первый крафт лопаты резервирует две палки и булыжник: иначе GraphPost не защищает"
                        + " материал внутри одного прохода графа Иначе тест take_OnePass_DoesNotSpendSameStickTwice"
                        + " перестанет ловить живой случай 25.11. При смене"
                        + " take_OnePass_DoesNotSpendSameStickTwice проверь GraphPost.submit.");
        assertTrue(
                BoardReserve.take(stock, held, Map.of("stick", 2, "cobblestone", 1)),
                "Второй крафт получает остаток запаса: бронь не должна считать уже потраченные"
                        + " палки свободными Иначе тест take_OnePass_DoesNotSpendSameStickTwice перестанет"
                        + " ловить эту поломку. При смене take_OnePass_DoesNotSpendSameStickTwice проверь"
                        + " BoardReserve.free.");
        assertFalse(
                BoardReserve.take(stock, held, Map.of("stick", 1)),
                "Третий крафт без палок не ставится на доску: иначе дух снова получит заведомо"
                        + " невыполнимую задачу Иначе тест take_OnePass_DoesNotSpendSameStickTwice"
                        + " перестанет ловить эту поломку. При смене"
                        + " take_OnePass_DoesNotSpendSameStickTwice проверь GraphPost.waiting.");
        assertEquals(
                Map.of("stick", 4, "cobblestone", 2),
                held,
                "Отказ не добавляет частичную бронь: незавершённый набор не должен удерживать"
                        + " материалы и блокировать следующий крафт Иначе тест"
                        + " take_OnePass_DoesNotSpendSameStickTwice перестанет ловить эту поломку."
                        + " При смене take_OnePass_DoesNotSpendSameStickTwice проверь атомарность take.");
    }

    @Test
    void release_RejectedTicket_ReturnsRequirements() {
        Map<String, Integer> held = new HashMap<>(Map.of("stick", 2));
        BoardReserve.release(held, Map.of("minecraft:stick", 2));

        assertEquals(
                Map.of(),
                held,
                "Отклонённый билет освобождает бронь: иначе BACKOFF или DUPLICATE навсегда"
                        + " списывают палки из запаса прохода Иначе тест release_RejectedTicket_ReturnsRequirements"
                        + " перестанет ловить эту поломку. При смене"
                        + " release_RejectedTicket_ReturnsRequirements проверь GraphPost.submit.");
    }

    @Test
    void unreserved_FenceHoldsLogs_TorchesDoNotBurnThem() {
        Board board = new Board(() -> 0);
        board.submit(ticket("fence", BoardCase.BUILD_FENCE, Map.of("jungle_log", 4)));

        StockView stock = BoardReserve.unreserved(new StockView(Map.of("jungle_log", 4)), board);

        assertEquals(
                Map.of(),
                stock.available(),
                "Брёвна под забор забронированы: источникам доски (граф и уголь для факелов) они не видны как свободные "
                        + "(приёмка «джунгли»)"
                        + " Иначе тест unreserved_FenceHoldsLogs_TorchesDoNotBurnThem перестанет ловить эту поломку. При смене unreserved_FenceHoldsLogs_TorchesDoNotBurnThem проверь это утверждение.");
    }
}
