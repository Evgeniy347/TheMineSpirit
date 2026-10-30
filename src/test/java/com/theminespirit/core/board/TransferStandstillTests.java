package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransferStandstillTests {
    private static final UUID FIRST = new UUID(0, 1);
    private static final UUID CRAFTER = new UUID(0, 2);
    private static final UUID SECOND = new UUID(0, 3);
    private static final String CRAFT = "graph:craft_stone_pickaxe@" + CRAFTER;
    private static final EatNeed FED = new EatNeed(20, 20, 20, 20);

    private static Board board() {
        Board board = new Board(() -> 0);
        board.submit(new BoardTicket(
                CRAFT,
                BoardCase.CRAFT_STONE_PICKAXE,
                Optional.of(CRAFTER),
                BoardRules.priorityOf(BoardCase.CRAFT_STONE_PICKAXE),
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of()));
        transfer(board, "stick", FIRST);
        transfer(board, "cobblestone", SECOND);
        return board;
    }

    private static void transfer(Board board, String item, UUID holder) {
        board.submit(new BoardTicket(
                "transfer:" + CRAFT + ":" + item + ":" + holder,
                BoardCase.TRANSFER_ITEM,
                Optional.of(holder),
                BoardRules.priorityOf(BoardCase.TRANSFER_ITEM),
                0,
                1,
                TransferCarriers.cause(CRAFT),
                List.of(),
                Map.of(),
                TransferTicket.modifiers(CRAFTER, item, 1, holder)));
    }

    private static List<DispatchRound.Candidate> spirits() {
        return List.of(
                new DispatchRound.Candidate(FIRST, "spirit-1", null, List.of(), FED, SpiritView.free()),
                new DispatchRound.Candidate(CRAFTER, "spirit-2", null, List.of(), FED, SpiritView.free()),
                new DispatchRound.Candidate(SECOND, "spirit-3", null, List.of(), FED, SpiritView.free()));
    }

    @Test
    void carrying_TransferPendingWithHolder_HolderNotCounted() {
        Board board = board();

        assertEquals(
                Set.of(),
                TransferCarriers.carrying(board, CRAFT, Set.of()),
                "Держатель ещё не взятой передачи ничего не несёт: записав его носильщиком, раунд держит его "
                        + "ожиданием и не даёт взять саму передачу — доска стоит");
    }

    @Test
    void dispatch_FreeSpiritsAndOpenTransfers_AtLeastOneTicketIssued() {
        Board board = board();
        int issued = 0;
        for (int round = 0; round < 3; round++) {
            issued += issueRound(board, spirits(), round * BoardTuning.boardIntervalTicks());
        }

        assertTrue(
                issued > 0,
                "Доска стоит: при трёх свободных духах и открытом крафте с передачами за 3 раунда не выдано ни "
                        + "одного задания (PENDING=" + pending(board) + ", ASSIGNED=" + board.assignedCounts() + ")");
    }

    private static int pending(Board board) {
        return (int) board.all().stream()
                .filter(ticket -> board.statusOf(ticket.id()).orElse(null) == BoardStatus.PENDING)
                .count();
    }

    private static int issueRound(Board board, List<DispatchRound.Candidate> spirits, long now) {
        DispatchRound round = new DispatchRound(board, now);
        int issued = 0;
        for (BoardTicket ticket : DispatchRound.order(board.claimable(new StockView(Map.of()), now))) {
            Optional<DispatchRound.Candidate> chosen = round.choose(ticket, spirits);
            if (chosen.isEmpty()) {
                board.noteWait(ticket.id(), round.why(ticket, spirits));
                continue;
            }
            if (awaitsTransfers(board, ticket)) {
                for (UUID carrier : TransferCarriers.carrying(board, ticket.id(), Set.of())) {
                    round.await(carrier, "несёт ингредиенты для " + ticket.id());
                }
                continue;
            }
            if (board.claim(ticket.id(), chosen.orElseThrow().id(), now) == BoardOutcome.CLAIMED) {
                round.take(chosen.orElseThrow().id());
                issued++;
            }
        }
        return issued;
    }

    private static boolean awaitsTransfers(Board board, BoardTicket ticket) {
        if (ticket.boardCase() == BoardCase.TRANSFER_ITEM) {
            return false;
        }
        for (BoardTicket other : board.all()) {
            BoardStatus status = board.statusOf(other.id()).orElse(null);
            if (other.boardCase() == BoardCase.TRANSFER_ITEM
                    && TransferCarriers.cause(ticket.id()).equals(other.cause())
                    && (status == BoardStatus.PENDING
                            || status == BoardStatus.WAITING_RESOURCES
                            || status == BoardStatus.ASSIGNED)) {
                return true;
            }
        }
        return false;
    }
}
