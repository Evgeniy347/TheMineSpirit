package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BoardTransitionsTests {
    private static BoardTicket activity(String id) {
        return new BoardTicket(
                id,
                BoardCase.ACTIVITY_SMELT,
                Optional.empty(),
                BoardRules.priorityOf(BoardCase.ACTIVITY_SMELT),
                0,
                1,
                "activity",
                List.of(),
                Map.of());
    }

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

        assertTrue(
                line.contains("DONE"),
                "latest.log обязан писать переход DONE: без него видно только выдачу"
                        + " Иначе тест line_Done_NamesTicketAttemptsAndCause перестанет ловить эту поломку. При смене line_Done_NamesTicketAttemptsAndCause проверь это утверждение.");
        assertTrue(
                line.contains("first:build_mine"),
                "Строка перехода называет id задания: по нему сверяют счётчик DONE с доской"
                        + " Иначе тест line_Done_NamesTicketAttemptsAndCause перестанет ловить эту поломку. При смене line_Done_NamesTicketAttemptsAndCause проверь это утверждение.");
        assertTrue(
                line.contains("попыток=4"),
                "Строка перехода называет попытки: по ним видно историю задания"
                        + " Иначе тест line_Done_NamesTicketAttemptsAndCause перестанет ловить эту поломку. При смене line_Done_NamesTicketAttemptsAndCause проверь это утверждение.");
        assertTrue(
                line.contains("причина="),
                "Строка перехода называет причину: без неё переход не объяснён"
                        + " Иначе тест line_Done_NamesTicketAttemptsAndCause перестанет ловить эту поломку. При смене line_Done_NamesTicketAttemptsAndCause проверь это утверждение.");
    }

    @Test
    void line_Failed_IsNotDuplicatedByTransition() {
        assertTrue(
                BoardTransitions.line(
                                BoardLog.Event.FAILED,
                                ticket("first:build_orchard").withFailure("не удалось добыть charcoal ×1"),
                                3,
                                "spirit-3")
                        .isEmpty(),
                "FAILED уже пишет [incident] с причиной, снимком и инвентарём: вторая строка на тот же провал "
                        + "делит лог на три части и прячет причину (observe18 B10)"
                        + " Иначе тест line_Failed_IsNotDuplicatedByTransition перестанет ловить эту поломку. При смене line_Failed_IsNotDuplicatedByTransition проверь это утверждение.");
    }

    @Test
    void line_CancelledActivity_IsLogged() {
        assertTrue(
                BoardTransitions.line(BoardLog.Event.CANCELLED, activity("graph:event:night:guard"), 0, "spirit-1")
                        .isPresent(),
                "Отмена занятия — такая же отмена задания доски: владелец обязан видеть, почему охрана или поход "
                        + "исчезли с доски"
                        + " Иначе тест line_CancelledActivity_IsLogged перестанет ловить эту поломку. При смене line_CancelledActivity_IsLogged проверь это утверждение.");
    }

    @Test
    void line_CancelledBoardTask_IsLogged() {
        assertTrue(
                BoardTransitions.line(BoardLog.Event.CANCELLED, ticket("supply_gather:oak_planks"), 0, null)
                        .isPresent(),
                "Настоящая отмена задания доски остаётся в логе: иначе пропадёт ответ, почему задача исчезла"
                        + " При смене line_CancelledBoardTask_IsLogged проверь это утверждение.");
    }

    @Test
    void line_Reopened_NamesTicketAndCause() {
        String line = BoardTransitions.line(BoardLog.Event.REOPENED, ticket("repair:build_house"), 0, null)
                .orElseThrow();

        assertTrue(
                line.contains("REOPENED"),
                "latest.log обязан писать переход REOPENED: откат DONE не должен быть немым"
                        + " Иначе тест line_Reopened_NamesTicketAndCause перестанет ловить эту поломку. При смене line_Reopened_NamesTicketAndCause проверь это утверждение.");
        assertTrue(
                line.contains("repair:build_house"),
                "Строка REOPENED называет id: по нему находят законный ремонт, снявший DONE"
                        + " Иначе тест line_Reopened_NamesTicketAndCause перестанет ловить эту поломку. При смене line_Reopened_NamesTicketAndCause проверь это утверждение.");
        assertTrue(
                line.contains("причина="),
                "Строка REOPENED называет причину: без неё откат DONE не объяснён"
                        + " Иначе тест line_Reopened_NamesTicketAndCause перестанет ловить эту поломку. При смене line_Reopened_NamesTicketAndCause проверь это утверждение.");
    }

    @Test
    void line_AssignedAndBlocked_NotDuplicated() {
        assertTrue(
                BoardTransitions.line(BoardLog.Event.ASSIGNED, ticket("first:build_mine"), 0, "spirit-1")
                        .isEmpty(),
                "ASSIGNED уже пишет отдельная строка выбора духа: вторая строка на тот же переход раздваивает лог"
                        + " Иначе тест line_AssignedAndBlocked_NotDuplicated перестанет ловить эту поломку. При смене line_AssignedAndBlocked_NotDuplicated проверь это утверждение.");
        assertTrue(
                BoardTransitions.line(BoardLog.Event.BLOCKED, ticket("first:build_mine"), 5, null)
                        .isEmpty(),
                "BLOCKED уже пишет отдельная строка с историей попыток: вторая строка на тот же переход "
                        + "раздувает latest.log"
                        + " Иначе тест line_AssignedAndBlocked_NotDuplicated перестанет ловить эту поломку. При смене line_AssignedAndBlocked_NotDuplicated проверь это утверждение.");
    }
}
