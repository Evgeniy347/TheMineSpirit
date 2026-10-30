package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WaitNotesTests {
    @Test
    void waitNote_PendingTicketNotClaimable_StaleNoteRemoved() {
        Board board = new Board(() -> 0);
        board.submit(new BoardTicket(
                "build_1",
                BoardCase.BUILD_HOUSE,
                Optional.empty(),
                BoardRules.priorityOf(BoardCase.BUILD_HOUSE),
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of("clear:1"),
                Map.of()));
        board.noteWait("build_1", "spirit-2 — несёт ингредиенты для graph:craft_stone_pickaxe@c48663b4");

        board.claimable(new StockView(Map.of()), 0);

        assertEquals(
                Optional.empty(),
                board.waitNote("build_1"),
                "Причина ожидания прошлых раундов снимается, когда задание больше не выдаётся: иначе устаревшая "
                        + "фраза висит на десятках заданий и врёт наблюдателю");
    }
}
