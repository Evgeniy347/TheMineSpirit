package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        board.submit(ticket("table", BoardCase.CRAFTING_STATION, Map.of("jungle_planks", 4)));
        board.claim("table", UUID.randomUUID(), 0);
        board.complete("table");

        assertEquals(
                Map.of("jungle_planks", 16),
                BoardReserve.items(board),
                "Бронируют только открытые задания: закрытый верстак досок не держит");
    }

    @Test
    void free_StockWithFullIds_SubtractsReservedShortIds() {
        Map<String, Integer> stock = Map.of("minecraft:jungle_planks", 10, "minecraft:stick", 3);

        Map<String, Integer> free = BoardReserve.free(stock, Map.of("jungle_planks", 16, "stick", 1));

        assertEquals(Map.of("minecraft:stick", 2), free, "Бронь в коротких id вычитается из полных id запаса");
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
                        + "(приёмка «джунгли»)");
    }
}
