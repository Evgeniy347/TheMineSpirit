package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BoardTransitionsTests {
    private static BoardTicket ticket(String id) {
        return new BoardTicket(
                id,
                BoardCase.BUILD_MINE,
                Optional.empty(),
                BoardRules.priorityOf(BoardCase.BUILD_MINE),
                0,
                1,
                "first",
                List.of(),
                Map.of());
    }

    @Test
    void line_Done_NamesTicketAttemptsAndCause() {
        String line = BoardTransitions.line(BoardLog.Event.DONE, ticket("first:build_mine"), 4, "spirit-2")
                .orElseThrow();

        assertTrue(line.contains("DONE"), "latest.log обязан писать переход DONE: без него видно только выдачу");
        assertTrue(
                line.contains("first:build_mine"),
                "Строка перехода называет id задания: по нему сверяют счётчик DONE с доской");
        assertTrue(line.contains("попыток=4"), "Строка перехода называет попытки: по ним видно историю задания");
        assertTrue(line.contains("причина="), "Строка перехода называет причину: без неё переход не объяснён");
    }

    @Test
    void line_Failed_NamesFailureReason() {
        String line = BoardTransitions.line(
                        BoardLog.Event.FAILED,
                        ticket("first:build_orchard").withFailure("не удалось добыть charcoal ×1"),
                        3,
                        "spirit-3")
                .orElseThrow();

        assertTrue(line.contains("FAILED"), "latest.log обязан писать переход FAILED: иначе провал ищут в board-log");
        assertTrue(
                line.contains("не удалось добыть charcoal ×1"),
                "Строка FAILED повторяет причину провала: по ней чинят механику, а не угадывают");
        assertTrue(
                line.contains("попыток=3"), "Строка FAILED называет число провалов: по нему видно предел блокировки");
    }

    @Test
    void line_Reopened_NamesTicketAndCause() {
        String line = BoardTransitions.line(BoardLog.Event.REOPENED, ticket("repair:build_house"), 0, null)
                .orElseThrow();

        assertTrue(
                line.contains("REOPENED"),
                "latest.log обязан писать переход REOPENED: откат DONE не должен быть немым");
        assertTrue(
                line.contains("repair:build_house"),
                "Строка REOPENED называет id: по нему находят законный ремонт, снявший DONE");
        assertTrue(line.contains("причина="), "Строка REOPENED называет причину: без неё откат DONE не объяснён");
    }

    @Test
    void line_AssignedAndBlocked_NotDuplicated() {
        assertTrue(
                BoardTransitions.line(BoardLog.Event.ASSIGNED, ticket("first:build_mine"), 0, "spirit-1")
                        .isEmpty(),
                "ASSIGNED уже пишет отдельная строка выбора духа: вторая строка на тот же переход раздваивает лог");
        assertTrue(
                BoardTransitions.line(BoardLog.Event.BLOCKED, ticket("first:build_mine"), 5, null)
                        .isEmpty(),
                "BLOCKED уже пишет отдельная строка с историей попыток: вторая строка на тот же переход "
                        + "раздувает latest.log");
    }
}
