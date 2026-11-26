package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DispatchOwnerTests {
    private static final UUID OWNER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID HELPER = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final EatNeed FED = new EatNeed(20, 20, 20, 20);

    @Test
    void choose_GraphOwnerBusy_GivesWorkToFreeHelper() {
        BoardTicket ticket = ticket("graph:mine_cobble", "graph:event:more5_build_shortage", Optional.of(OWNER));
        Board board = new Board(() -> 0);
        board.submit(ticket);
        DispatchRound round = new DispatchRound(board, 0);
        DispatchRound.Candidate busy = candidate(OWNER, "spirit-1", "storage_build:storage");
        DispatchRound.Candidate free = candidate(HELPER, "spirit-2", null);

        assertEquals(
                Optional.of(HELPER),
                round.choose(ticket, List.of(busy, free)).map(DispatchRound.Candidate::id),
                "Занятый владелец графа не держит работу: иначе свободные духи простаивают, пока один "
                        + "строит склад. При смене choose_GraphOwnerBusy_GivesWorkToFreeHelper проверь "
                        + "DispatchRound.reassignable и DispatchPick.pick.");
    }

    @Test
    void choose_ExternalOwnerBusy_DoesNotReassignExplicitTask() {
        BoardTicket ticket = ticket("board-explicit", "always", Optional.of(OWNER));
        Board board = new Board(() -> 0);
        board.submit(ticket);
        DispatchRound round = new DispatchRound(board, 0);
        DispatchRound.Candidate busy = candidate(OWNER, "spirit-1", "storage_build:storage");
        DispatchRound.Candidate free = candidate(HELPER, "spirit-2", null);

        assertTrue(
                round.choose(ticket, List.of(busy, free)).isEmpty(),
                "Явная внешняя адресация остаётся жёсткой: иначе REST-задание уйдёт другому духу и "
                        + "исполнитель потеряет цель. При смене choose_ExternalOwnerBusy_DoesNotReassignExplicitTask "
                        + "проверь ветку не-graph в DispatchRound.reassignable.");
    }

    @Test
    void why_GraphOwnerBusyAndHelperFree_NamesFreeHelper() {
        BoardTicket ticket = ticket("graph:mine_cobble", "graph:event:more5_build_shortage", Optional.of(OWNER));
        Board board = new Board(() -> 0);
        board.submit(ticket);
        DispatchRound round = new DispatchRound(board, 0);
        DispatchRound.Candidate busy = candidate(OWNER, "spirit-1", "storage_build:storage");
        DispatchRound.Candidate free = candidate(HELPER, "spirit-2", null);

        String why = round.why(ticket, List.of(busy, free));

        assertTrue(
                why.contains("spirit-2"),
                "Причина ожидания называет свободного помощника: иначе оператор снова видит ложное "
                        + "«заняты 1» при пяти свободных духах. При смене why_GraphOwnerBusyAndHelperFree_NamesFreeHelper "
                        + "проверь DispatchWhy.text.");
        assertFalse(
                why.contains("заняты 1"),
                "Свободный кандидат не маскируется под занятого: иначе диагностика отправляет чинить "
                        + "не ту причину. При смене why_GraphOwnerBusyAndHelperFree_NamesFreeHelper проверь ветку free.");
    }

    private static BoardTicket ticket(String id, String cause, Optional<UUID> owner) {
        return new BoardTicket(
                id,
                BoardCase.GATHER_MATERIALS,
                owner,
                BoardRules.priorityOf(BoardCase.GATHER_MATERIALS),
                0,
                1,
                cause,
                List.of(),
                Map.of());
    }

    private static DispatchRound.Candidate candidate(UUID id, String name, String busy) {
        return new DispatchRound.Candidate(id, name, busy, List.of(), FED, SpiritView.free());
    }
}
