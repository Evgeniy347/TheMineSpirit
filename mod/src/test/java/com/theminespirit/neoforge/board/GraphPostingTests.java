package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.api.BoardCase;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GraphPostingTests {
    @Test
    void retirable_UnpostedPendingGraphTicket_IsRetired() {
        BoardTicket ticket = ticket("graph:station:crafting_table");

        assertTrue(
                GraphPosting.retirable(ticket, Set.of(), BoardStatus.PENDING),
                "Невыполнимый graph-билет снимается с доски: иначе свободный дух берёт задание без материала "
                        + "и снова уходит в паузу кейса. При смене retirable_UnpostedPendingGraphTicket_IsRetired "
                        + "проверь GraphPosting.retireUnposted и живой crafting_table.");
    }

    @Test
    void retirable_PostedAssignedOrForeignTicket_IsKept() {
        BoardTicket graph = ticket("graph:station:crafting_table");
        BoardTicket foreign = ticket("storage_build:storage_3");

        assertFalse(
                GraphPosting.retirable(graph, Set.of(graph.id()), BoardStatus.PENDING),
                "Текущее задание графа остаётся на доске: иначе новая постройка не получает исполнителя. "
                        + "При смене retirable_PostedAssignedOrForeignTicket_IsKept проверь GraphPosting.postTickets.");
        assertFalse(
                GraphPosting.retirable(graph, Set.of(), BoardStatus.ASSIGNED),
                "Уже запущенное задание не отменяется реconciliation: иначе дух теряет работу на полном шаге. "
                        + "При смене retirable_PostedAssignedOrForeignTicket_IsKept проверь GraphPosting.retireUnposted.");
        assertFalse(
                GraphPosting.retirable(foreign, Set.of(), BoardStatus.PENDING),
                "Чужой источник не отменяется графиком: иначе склад и расчистка исчезают из очереди. "
                        + "При смене retirable_PostedAssignedOrForeignTicket_IsKept проверь GraphPosting.retirable.");
    }

    private static BoardTicket ticket(String id) {
        return new BoardTicket(id, BoardCase.CRAFTING_STATION, Optional.empty(), 10, 1, 1, id, List.of(), Map.of());
    }
}
