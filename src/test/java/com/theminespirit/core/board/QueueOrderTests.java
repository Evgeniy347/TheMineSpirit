package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QueueOrderTests {
    private static BoardTicket ticket(String id, BoardCase boardCase, int priority, List<String> dependsOn) {
        return new BoardTicket(
                id, boardCase, Optional.empty(), priority, 0, 1, BoardTicket.ALWAYS_CAUSE, dependsOn, Map.of());
    }

    @Test
    void claimable_TicketPriority_OrdersOverCasePriority() {
        Board board = new Board(() -> 0);
        board.submit(ticket("gather:plain", BoardCase.GATHER_MATERIALS, 45, List.of()));
        board.submit(ticket("gather:urgent", BoardCase.GATHER_MATERIALS, 90, List.of()));

        List<String> order = board.claimable(new StockView(Map.of()), 0).stream()
                .map(BoardTicket::id)
                .toList();

        assertEquals(
                List.of("gather:urgent", "gather:plain"),
                order,
                "Выдача идёт по приоритету задания, а не только кейса: модификатор без влияния на порядок — скрытая механика");
    }

    @Test
    void claimable_Dependency_InheritsDependantPriority() {
        Board board = new Board(() -> 0);
        board.submit(ticket("fence", BoardCase.BUILD_FENCE, 50, List.of()));
        board.submit(ticket("gather:planks", BoardCase.GATHER_MATERIALS, 45, List.of()));
        board.submit(ticket("craft:pickaxe", BoardCase.CRAFT_WOODEN_PICKAXE, 55, List.of("gather:planks")));

        List<String> order = board.claimable(new StockView(Map.of()), 0).stream()
                .map(BoardTicket::id)
                .toList();

        assertEquals(
                List.of("gather:planks", "fence"),
                order,
                "Предшественник наследует приоритет ждущего его задания: сбор под кирку (55) раньше ограды (50)");
    }

    @Test
    void prune_FailedDependency_CancelsDependants() {
        long[] clock = {0};
        Board board = new Board(() -> clock[0]);
        board.submit(ticket("gather:planks", BoardCase.GATHER_MATERIALS, 45, List.of()));
        board.submit(ticket("craft:pickaxe", BoardCase.CRAFT_WOODEN_PICKAXE, 55, List.of("gather:planks")));
        board.claim("gather:planks", new UUID(0, 1), 0);
        board.fail("gather:planks");

        board.prune(0);

        assertEquals(
                Optional.of(BoardStatus.CANCELLED),
                board.statusOf("craft:pickaxe"),
                "Задание, чей предшественник провален и убран, снимается, а не ждёт вечно: источник поставит цепочку заново");
    }
}
