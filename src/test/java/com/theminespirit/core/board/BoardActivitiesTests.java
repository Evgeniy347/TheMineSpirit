package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BoardActivitiesTests {
    @Test
    void targetOf_Plan_SaysWhereOrWhat() {
        TaskNode call = new ScriptCall("dig_out", Map.of("x", 10, "y", 61, "z", -5));

        assertEquals(
                "10,61,-5",
                BoardActivities.targetOf(call),
                "Цель карточки — координаты плана: по ней владелец видит, куда дух идёт, а не «внешнее действие»");
    }

    @Test
    void cardScript_GotoThenWork_NamesWorkNotApproach() {
        TaskNode plan = new SequenceNode(List.of(new ScriptCall("goto", Map.of()), new ScriptCall("smelt", Map.of())));

        assertEquals(
                "smelt",
                BoardActivities.cardScript("goto", plan),
                "Карточка занятия с планом «подход, потом плавка» обязана называть плавку: на плитке виден goto, "
                        + "владелец не понимает, чем дух занят");
        assertEquals(
                "goto",
                BoardActivities.cardScript("goto", null),
                "Без плана карточка называет текущий скрипт: команда «goto» остаётся подходом");
    }

    @Test
    void submit_ActivityCase_RejectedAsNotTicket() {
        Board board = new Board(() -> 100);
        UUID spirit = UUID.fromString("11111111-2222-3333-4444-555555555555");
        BoardTicket card = new BoardTicket(
                "graph:test:sense",
                BoardCase.ACTIVITY_SENSE,
                Optional.of(spirit),
                50,
                0,
                1,
                "graph:test",
                List.of(),
                Map.of());

        assertEquals(
                BoardOutcome.NOT_A_TICKET,
                board.submit(card),
                "Занятие не становится билетом доски: иначе на доске снова появятся activity-карточки мимо графа");
        assertEquals(Optional.empty(), board.occupation(spirit), "Карточки занятия на доске больше нет");
    }

    @Test
    void held_NoAssignedTicket_Empty() {
        Board board = new Board(() -> 100);
        UUID spirit = UUID.fromString("11111111-2222-3333-4444-555555555555");

        assertEquals(
                Optional.empty(),
                BoardHeld.of(board, spirit),
                "Без билета доски держателя нет: занятие больше не подменяет задание в REST");
    }
}
