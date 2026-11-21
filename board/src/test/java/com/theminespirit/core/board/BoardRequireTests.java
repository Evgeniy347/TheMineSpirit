package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BoardRequireTests {
    private static final String LIVE_BOARD = "/live/observe12-minereopen/board.json";

    @Test
    void require_LiveFence_ReleasesBlockedTicketWithoutStalePlanks() throws IOException {
        Board board = LiveBoardFixture.load(LIVE_BOARD);
        BoardTicket fence = board.find("first:build_fence")
                .orElseThrow(() -> new AssertionError("В живой доске observe12 нет first:build_fence"));
        assertFalse(
                fence.requiredItems().isEmpty(),
                "Живая доска observe12 держит ограду с устаревшим требованием " + fence.requiredItems()
                        + " Иначе тест require_LiveFence_ReleasesBlockedTicketWithoutStalePlanks перестанет ловить эту поломку. При смене require_LiveFence_ReleasesBlockedTicketWithoutStalePlanks проверь это утверждение.");
        assertEquals(
                BoardStatus.PENDING,
                board.statusOf("first:build_fence").orElseThrow(),
                "Старая блокировка без типизированной нужды не восстанавливается как BLOCKED: условие "
                        + "пересчитывается, иначе доска ждёт пустоту вечно (живая доска observe12 хранит такой снимок)"
                        + " При смене require_LiveFence_ReleasesBlockedTicketWithoutStalePlanks проверь это утверждение.");

        assertTrue(
                board.require("first:build_fence", Map.of()),
                "Освежение материалов обязано принять пустое требование: ограда уже стоит 26 из 26"
                        + " Иначе тест require_LiveFence_ReleasesBlockedTicketWithoutStalePlanks перестанет ловить эту поломку. При смене require_LiveFence_ReleasesBlockedTicketWithoutStalePlanks проверь это утверждение.");

        assertEquals(
                Map.of(),
                board.find("first:build_fence").orElseThrow().requiredItems(),
                "Устаревшие oak_planks/stick сняты: иначе задача ждёт доски при готовой ограде"
                        + " При смене require_LiveFence_ReleasesBlockedTicketWithoutStalePlanks проверь это утверждение.");
        List<BoardTicket> ready = board.claimable(new StockView(Map.of()), 0);
        assertTrue(
                ready.stream().anyMatch(ticket -> "first:build_fence".equals(ticket.id())),
                "После освежения ограда снова доступна к выдаче: "
                        + ready.stream().map(BoardTicket::id).toList()
                        + " Иначе тест require_LiveFence_ReleasesBlockedTicketWithoutStalePlanks перестанет ловить эту поломку. При смене require_LiveFence_ReleasesBlockedTicketWithoutStalePlanks проверь это утверждение.");
    }

    @Test
    void require_AssignedMine_LeavesTicketAlone() throws IOException {
        Board board = LiveBoardFixture.load(LIVE_BOARD);
        BoardTicket mine = board.find("first:build_mine")
                .orElseThrow(() -> new AssertionError("В живой доске observe12 нет first:build_mine"));
        assertEquals(
                57,
                mine.requiredItems().get("cobblestone"),
                "Живая шахта требует 57 полов: " + mine
                        + " Иначе тест require_AssignedMine_LeavesTicketAlone перестанет ловить эту поломку. При смене require_AssignedMine_LeavesTicketAlone проверь это утверждение.");

        assertFalse(
                board.require("first:build_mine", Map.of()),
                "Материалы задания в работе не переписываются: дух уже держит контракт"
                        + " Иначе тест require_AssignedMine_LeavesTicketAlone перестанет ловить эту поломку. При смене require_AssignedMine_LeavesTicketAlone проверь это утверждение.");
        assertEquals(
                Map.of("cobblestone", 57),
                board.find("first:build_mine").orElseThrow().requiredItems(),
                "Требование работающей шахты осталось прежним"
                        + " Иначе тест require_AssignedMine_LeavesTicketAlone перестанет ловить эту поломку. При смене require_AssignedMine_LeavesTicketAlone проверь это утверждение.");
    }
}
