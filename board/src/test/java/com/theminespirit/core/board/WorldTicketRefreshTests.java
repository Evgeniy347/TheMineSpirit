package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.board.api.BoardCase;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WorldTicketRefreshTests {
    @Test
    void submit_PendingFenceChanged_RefreshesInPlace() {
        Board board = new Board(() -> 0L);
        BoardTicket first = ticket(FenceRepairs.TICKET_ID, BoardCase.REPAIR_FENCE, Map.of("minecraft:oak_log", 4));
        BoardTicket wider = ticket(FenceRepairs.TICKET_ID, BoardCase.REPAIR_FENCE, Map.of("minecraft:oak_log", 80));

        assertEquals(
                BoardOutcome.ACCEPTED,
                board.submit(first),
                "Первая заявка ограды обязана встать на доску: без неё дух не узнает о проломах"
                        + " Иначе тест submit_PendingFenceChanged_RefreshesInPlace перестанет ловить эту поломку. При смене submit_PendingFenceChanged_RefreshesInPlace проверь это утверждение.");
        assertEquals(
                BoardOutcome.ACCEPTED,
                board.submit(wider),
                "Открытая мировая заявка обновляется на месте: иначе новые проломы ждали бы взятия старой"
                        + " При смене submit_PendingFenceChanged_RefreshesInPlace проверь это утверждение.");
        assertEquals(
                BoardStatus.PENDING,
                board.statusOf(FenceRepairs.TICKET_ID).orElseThrow(),
                "Обновлённая заявка остаётся PENDING: её ещё никто не взял"
                        + " Иначе тест submit_PendingFenceChanged_RefreshesInPlace перестанет ловить эту поломку. При смене submit_PendingFenceChanged_RefreshesInPlace проверь это утверждение.");
        assertEquals(
                Map.of("minecraft:oak_log", 80),
                board.tickets().stream()
                        .filter(ticket -> FenceRepairs.TICKET_ID.equals(ticket.id()))
                        .findFirst()
                        .orElseThrow()
                        .requiredItems(),
                "Доска хранит свежий расход: старый гейт пропустил бы недобор на новые клетки"
                        + " Иначе тест submit_PendingFenceChanged_RefreshesInPlace перестанет ловить эту поломку. При смене submit_PendingFenceChanged_RefreshesInPlace проверь это утверждение.");
    }

    @Test
    void submit_PendingCraftChanged_StaysDuplicate() {
        Board board = new Board(() -> 0L);
        BoardTicket first = ticket("stock:chests", BoardCase.STOCK_CHESTS, Map.of("minecraft:oak_planks", 4));
        BoardTicket wider = ticket("stock:chests", BoardCase.STOCK_CHESTS, Map.of("minecraft:oak_planks", 40));

        assertEquals(
                BoardOutcome.ACCEPTED,
                board.submit(first),
                "Первая заявка сундуков встаёт на доску"
                        + " Иначе тест submit_PendingCraftChanged_StaysDuplicate перестанет ловить эту поломку. При смене submit_PendingCraftChanged_StaysDuplicate проверь это утверждение.");
        assertEquals(
                BoardOutcome.DUPLICATE,
                board.submit(wider),
                "Не-мировые кейсы не обновляются подменой деталей: их работу ставят заново по факту нужды"
                        + " Иначе тест submit_PendingCraftChanged_StaysDuplicate перестанет ловить эту поломку. При смене submit_PendingCraftChanged_StaysDuplicate проверь это утверждение.");
    }

    private static BoardTicket ticket(String id, BoardCase boardCase, Map<String, Integer> requiredItems) {
        return new BoardTicket(
                id,
                boardCase,
                Optional.empty(),
                BoardRules.priorityOf(boardCase),
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                requiredItems);
    }
}
