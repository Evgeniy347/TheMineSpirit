package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QueueOrderTests {
    private static BoardTicket ticket(String id, BoardCase boardCase, int priority, List<String> dependsOn) {
        return ticket(id, boardCase, priority, dependsOn, 0);
    }

    private static BoardTicket ticket(
            String id, BoardCase boardCase, int priority, List<String> dependsOn, long createdAt) {
        return new BoardTicket(
                id, boardCase, Optional.empty(), priority, createdAt, 1, BoardTicket.ALWAYS_CAUSE, dependsOn, Map.of());
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
    void order_TicketWaitingLongerThanStarveTicks_GoesBeforeFreshHighPriority() {
        BoardTicket stale = ticket("pick_berries", BoardCase.GATHER_MATERIALS, 45, List.of(), 0);
        BoardTicket fresh = ticket("first:build_mine", BoardCase.BUILD_MINE, 50, List.of(), 13_000);
        Map<String, BoardStatus> statuses = Map.of(stale.id(), BoardStatus.PENDING, fresh.id(), BoardStatus.PENDING);

        List<String> order = List.of(stale, fresh).stream()
                .sorted(QueueOrder.of(List.of(stale, fresh), statuses, 13_000))
                .map(BoardTicket::id)
                .toList();

        assertEquals(
                List.of("pick_berries", "first:build_mine"),
                order,
                "Задание, ждущее дольше starveTicks, выдаётся раньше свежего приоритетного: иначе низкий кейс "
                        + "висит в PENDING всё окно, пока духи берут новые стройки (аномалия 7)");
    }

    @Test
    void order_TicketWaitingLessThanStarveTicks_KeepsPriorityOrder() {
        BoardTicket stale = ticket("pick_berries", BoardCase.GATHER_MATERIALS, 45, List.of(), 0);
        BoardTicket fresh = ticket("first:build_mine", BoardCase.BUILD_MINE, 50, List.of(), 1_000);

        List<String> order = List.of(stale, fresh).stream()
                .sorted(QueueOrder.of(
                        List.of(stale, fresh),
                        Map.of(stale.id(), BoardStatus.PENDING, fresh.id(), BoardStatus.PENDING),
                        1_000))
                .map(BoardTicket::id)
                .toList();

        assertEquals(
                List.of("first:build_mine", "pick_berries"),
                order,
                "До порога голодания очередь идёт по приоритету: свежая важная работа не ждёт старую мелочь");
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
