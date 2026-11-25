package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.AllocationCases.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.ActivityTickets;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardActivities;
import com.theminespirit.core.board.BoardReserve;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.plan.ScriptCall;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActivityBoardScenarioPart2Tests {
    @Test
    void activityTicket_RequiredTool_ReservesItemForOpenJob() {
        Board board = new Board(() -> 0);
        UUID spirit = id("spirit-1");
        String cause = "graph:event:gear_equip_best:equip_best:" + spirit;
        BoardTicket ticket = ActivityTickets.ticket(new ActivityTickets.Request(
                new ActivityTickets.Identity(cause + ":" + spirit, BoardCase.ACTIVITY_ARM, cause),
                new ActivityTickets.Crew(Optional.of(spirit), List.of()),
                34,
                0,
                new ActivityTickets.Plan("equip", "", 100, new ScriptCall("equip", Map.of())),
                Map.of("wooden_pickaxe", 1)));
        board.submit(ticket);

        assertEquals(
                Map.of("wooden_pickaxe", 1),
                BoardReserve.items(board),
                "Открытое занятие обязано бронировать инструмент: иначе второй дух получит тот же"
                        + " последний предмет и withdraw упадёт с нулём Иначе тест"
                        + " activityTicket_RequiredTool_ReservesItemForOpenJob перестанет ловить живой"
                        + " случай 25.11. При смене activityTicket_RequiredTool_ReservesItemForOpenJob"
                        + " проверь ActivityTickets.ticket и BoardReserve.items.");
    }

    @Test
    void openCause_PendingFarmActivity_BlocksDuplicatePlanting() {
        Board board = new Board(() -> 0);
        String cause = "graph:event:field_empty:plant_crop";
        UUID spirit = id("spirit-1");
        String ticketId = cause + ":" + spirit;
        board.submit(ActivityTickets.ticket(new ActivityTickets.Request(
                new ActivityTickets.Identity(ticketId, BoardCase.ACTIVITY_FARM, cause),
                new ActivityTickets.Crew(Optional.of(spirit), List.of()),
                35,
                0,
                new ActivityTickets.Plan("plant_crop", "", 200, new ScriptCall("plant_crop", Map.of())),
                Map.of())));

        assertTrue(
                BoardActivities.open(board, cause),
                "Открытое занятие с тем же узлом графа обязано блокировать повторную посадку: иначе"
                        + " второй дух получит ту же грядку и упадёт с «пустой земли нет» Иначе тест"
                        + " openCause_PendingFarmActivity_BlocksDuplicatePlanting перестанет ловить живой"
                        + " случай 25.11. При смене openCause_PendingFarmActivity_BlocksDuplicatePlanting"
                        + " проверь GraphActivityPost.post и BoardActivities.open.");
        assertFalse(
                BoardActivities.open(board, "graph:event:crop_ready:harvest_crop"),
                "Другое событие не блокируется открытой посадкой: иначе сбор урожая будет ждать"
                        + " постороннее задание Иначе тест openCause_PendingFarmActivity_BlocksDuplicatePlanting"
                        + " перестанет ловить эту поломку. При смене"
                        + " openCause_PendingFarmActivity_BlocksDuplicatePlanting проверь сравнение cause.");
        assertTrue(
                BoardActivities.openId(board, ticketId),
                "Открытый id занятия признаётся обновляемым: иначе GraphActivityPost не сможет"
                        + " пополнить команду уже выставленного похода Иначе тест"
                        + " openCause_PendingFarmActivity_BlocksDuplicatePlanting перестанет ловить"
                        + " эту поломку. При смене openCause_PendingFarmActivity_BlocksDuplicatePlanting"
                        + " проверь GraphExpeditionPost и BoardActivities.openId.");
        board.claim(ticketId, spirit, 0);
        board.complete(ticketId);
        assertFalse(
                BoardActivities.openId(board, ticketId),
                "Закрытый id не считается открытым: иначе старый билет того же духа будет вечно"
                        + " обходить защиту от дубликата cause Иначе тест"
                        + " openCause_PendingFarmActivity_BlocksDuplicatePlanting перестанет ловить"
                        + " повтор till_soil от 25.11. При смене"
                        + " openCause_PendingFarmActivity_BlocksDuplicatePlanting проверь openId.");
    }
}
