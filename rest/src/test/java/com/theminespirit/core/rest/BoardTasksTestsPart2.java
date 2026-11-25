package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.ActivityTickets;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.plan.ScriptCall;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BoardTasksTestsPart2 {
    private static final UUID SPIRIT = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void of_ExpeditionWaitingForCrew_ShowsTeamAndReason() {
        Board board = new Board(() -> 0);
        UUID leader = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
        List<UUID> crew = List.of(leader, SPIRIT);
        String id = "graph:event:expedition_ready:expedition";
        BoardTicket expedition = ActivityTickets.ticket(new ActivityTickets.Request(
                new ActivityTickets.Identity(id, BoardCase.ACTIVITY_EXPEDITION, id),
                new ActivityTickets.Crew(Optional.of(leader), crew),
                100,
                0,
                new ActivityTickets.Plan("mine_vein", "mine", 6000, new ScriptCall("mine_vein", Map.of())),
                Map.of()));
        board.submit(expedition);
        board.holds().hold(expedition.id(), "нет команды: нужно 2 духа, рядом 1");

        BoardTask task = BoardTasks.of(board, expedition, BoardStatus.PENDING, new StockView(Map.of()));

        assertEquals(
                crew.stream().map(UUID::toString).toList(),
                task.executors(),
                "REST отдаёт всю команду похода"
                        + " Иначе тест of_ExpeditionWaitingForCrew_ShowsTeamAndReason перестанет ловить эту поломку. При смене of_ExpeditionWaitingForCrew_ShowsTeamAndReason проверь это утверждение.");
        assertTrue(
                task.waitReason() != null && task.waitReason().contains("нет команды"),
                "REST показывает причину ожидания похода, а не пустой статус"
                        + " Иначе тест of_ExpeditionWaitingForCrew_ShowsTeamAndReason перестанет ловить эту поломку. При смене of_ExpeditionWaitingForCrew_ShowsTeamAndReason проверь это утверждение.");
    }
}
