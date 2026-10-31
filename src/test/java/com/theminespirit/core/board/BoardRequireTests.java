package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BoardRequireTests {
    private static final String LIVE_BOARD = "/live/observe12/board.json";

    @Test
    void require_LiveFence_ReleasesBlockedTicketWithoutStalePlanks() throws IOException {
        Board board = LiveBoardFixture.load(LIVE_BOARD);
        BoardTicket fence = board.find("first:build_fence")
                .orElseThrow(() -> new AssertionError("В живой доске observe12 нет first:build_fence"));
        assertFalse(
                fence.requiredItems().isEmpty(),
                "Живая доска observe12 держит ограду с устаревшим требованием " + fence.requiredItems());
        assertEquals(
                BoardStatus.BLOCKED,
                board.statusOf("first:build_fence").orElseThrow(),
                "Ограда в живой доске заблокирована после провалов: тест обязан идти по этому состоянию");

        assertTrue(
                board.require("first:build_fence", Map.of()),
                "Освежение материалов обязано принять пустое требование: ограда уже стоит 26 из 26");

        assertEquals(
                Map.of(),
                board.find("first:build_fence").orElseThrow().requiredItems(),
                "Устаревшие oak_planks/stick сняты: иначе задача ждёт доски при готовой ограде");
        List<BoardTicket> ready = board.claimable(new StockView(Map.of()), 0);
        assertTrue(
                ready.stream().anyMatch(ticket -> "first:build_fence".equals(ticket.id())),
                "После освежения ограда снова доступна к выдаче: "
                        + ready.stream().map(BoardTicket::id).toList());
    }

    @Test
    void require_AssignedMine_LeavesTicketAlone() throws IOException {
        Board board = LiveBoardFixture.load(LIVE_BOARD);
        BoardTicket mine = board.find("first:build_mine")
                .orElseThrow(() -> new AssertionError("В живой доске observe12 нет first:build_mine"));
        assertEquals(57, mine.requiredItems().get("cobblestone"), "Живая шахта требует 57 полов: " + mine);

        assertFalse(
                board.require("first:build_mine", Map.of()),
                "Материалы задания в работе не переписываются: дух уже держит контракт");
        assertEquals(
                Map.of("cobblestone", 57),
                board.find("first:build_mine").orElseThrow().requiredItems(),
                "Требование работающей шахты осталось прежним");
    }
}
