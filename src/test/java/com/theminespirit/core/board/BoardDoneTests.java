package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BoardDoneTests {
    private static final UUID SPIRIT = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static Board boardAt(long now) {
        return new Board(() -> now);
    }

    private static BoardTicket ticket(
            String id, BoardCase boardCase, long createdAt, String cause, Map<String, Integer> requiredItems) {
        return new BoardTicket(
                id,
                boardCase,
                Optional.empty(),
                BoardRules.priorityOf(boardCase),
                createdAt,
                1,
                cause,
                List.of(),
                requiredItems);
    }

    @Test
    void submit_SameDoneTicket_KeepsDone() {
        Board board = boardAt(0);
        board.submit(ticket(
                "graph:craft_stone_axe@spirit",
                BoardCase.CRAFT_STONE_AXE,
                0,
                "graph:craft_stone_axe@spirit",
                Map.of()));
        board.claim("graph:craft_stone_axe@spirit", SPIRIT, 0);
        board.complete("graph:craft_stone_axe@spirit");

        BoardOutcome outcome = board.submit(ticket(
                "graph:craft_stone_axe@spirit",
                BoardCase.CRAFT_STONE_AXE,
                100,
                "graph:craft_stone_axe@spirit",
                Map.of()));

        assertEquals(
                BoardOutcome.DUPLICATE,
                outcome,
                "Тот же id с тем же составом задания не переиздаётся: иначе DONE перезаписывается в PENDING "
                        + "с новым createdAt и счётчик выполненного падает (наблюдение 6, аномалия 7)");
        assertEquals(
                BoardStatus.DONE,
                board.statusOf("graph:craft_stone_axe@spirit").orElse(null),
                "Сданное задание остаётся DONE: повторная постановка без нового состава — не повод откатывать итог");
    }

    @Test
    void submit_AlwaysCauseDoneTicket_Reissued() {
        Board board = boardAt(0);
        board.submit(ticket("eat:spirit", BoardCase.EAT_TO_FULL, 0, BoardTicket.ALWAYS_CAUSE, Map.of()));
        board.claim("eat:spirit", SPIRIT, 0);
        board.complete("eat:spirit");

        BoardOutcome outcome =
                board.submit(ticket("eat:spirit", BoardCase.EAT_TO_FULL, 100, BoardTicket.ALWAYS_CAUSE, Map.of()));

        assertEquals(
                BoardOutcome.ACCEPTED,
                outcome,
                "Постоянная причина (cause=always) означает повторяющееся дело: поевший дух снова получает "
                        + "задание есть, когда проголодается, и запрет переиздания его бы не выпустил");
        assertEquals(
                BoardStatus.PENDING,
                board.statusOf("eat:spirit").orElse(null),
                "Повторяющееся задание после DONE ставится заново и ждёт выдачи");
    }

    @Test
    void submit_ChangedDoneTicket_Replaced() {
        Board board = boardAt(0);
        board.submit(ticket("build_1", BoardCase.BUILD_HOUSE, 0, "build_1", Map.of()));
        board.claim("build_1", SPIRIT, 0);
        board.complete("build_1");

        BoardOutcome outcome =
                board.submit(ticket("build_1", BoardCase.BUILD_HOUSE, 0, "build_1", Map.of("oak_log", 8)));

        assertEquals(
                BoardOutcome.ACCEPTED,
                outcome,
                "Билет с тем же id, но новым составом заменяет выполненный: иначе настоящая переделка "
                        + "(например, после отката мира) упиралась бы в DONE-тень и шаг не повторился бы никогда");
        assertEquals(
                BoardStatus.PENDING,
                board.statusOf("build_1").orElse(null),
                "Заменённый билет с новым составом снова ждёт выдачи, а не остаётся DONE");
        assertTrue(
                board.ledger().has(BoardCase.BUILD_HOUSE, 1),
                "Отметка ledger переживает замену: условие «постройка стоит» не теряется, когда "
                        + "задание переиздано");
    }
}
