package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DispatchForeignTaskTests {
    private static final BoardCase CASE = BoardCase.GATHER_MATERIALS;
    private static final EatNeed FED = new EatNeed(20, 20, 20, 20);

    @Test
    void choose_SpiritBusyWithForeignTask_NotGivenBoardWork() {
        BoardTicket ticket = new BoardTicket(
                "gather:1",
                CASE,
                Optional.empty(),
                BoardRules.priorityOf(CASE),
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());
        Board board = new Board(() -> 0);
        board.submit(ticket);
        DispatchRound round = new DispatchRound(board, 0);
        DispatchRound.Candidate busy = new DispatchRound.Candidate(
                new UUID(0, 1), "spirit-1", "gather:руда", List.of(), FED, SpiritView.free());

        assertTrue(
                round.choose(ticket, List.of(busy)).isEmpty(),
                "Дух, занятый задачей вне доски (REST, команда, граф), не получает ещё одно задание: две задачи "
                        + "на одном духе вытесняют друг друга, а REST-задача закрывается как вытесненная");
        String why = round.why(ticket, List.of(busy));
        assertTrue(
                why.contains("gather:руда"),
                "Причина ожидания называет чужую задачу духа, по ней видно, почему граф не получил духа: " + why);
    }
}
