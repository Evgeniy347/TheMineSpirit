package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SupplyGateTests {

    @Test
    void retains_ToolTierSupply_False() {
        BoardTicket stale = new BoardTicket(
                "supply_gather:pickaxe_luck",
                BoardCase.GATHER_MATERIALS,
                Optional.empty(),
                BoardRules.priorityOf(BoardCase.GATHER_MATERIALS),
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());

        assertFalse(
                SupplyGate.retains(stale, Set.of()),
                "Старое задание добычи маркера кирки снимается с доски, а не живёт вечно по причине always");
    }

    @Test
    void retains_OrdinaryAlwaysTicket_True() {
        BoardTicket ordinary = new BoardTicket(
                "clear:2",
                BoardCase.CLEAR_GROUNDS,
                Optional.empty(),
                BoardRules.priorityOf(BoardCase.CLEAR_GROUNDS),
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());

        assertTrue(
                SupplyGate.retains(ordinary, Set.of()),
                "Обычное задание с причиной always остаётся на доске: фильтр трогает только маркеры кирки");
    }

    @Test
    void isToolTierSupply_SupplyTicketWithMarker_True() {
        assertTrue(
                SupplyGate.isToolTierSupply("supply_gather:pickaxe_luck"),
                "Старый тикет добычи маркера кирки обязан сниматься с доски: предмета 'pickaxe_luck' не существует");
        assertTrue(
                SupplyGate.isToolTierSupply("supply:pickaxe_stone_tier"),
                "Снабжение маркером кирки — та же ошибка: его тоже снимают");
    }

    @Test
    void isToolTierSupply_OtherTickets_False() {
        assertFalse(
                SupplyGate.isToolTierSupply("supply_gather:dirt"),
                "Обычная добыча не снимается: фильтр трогает только маркеры инструмента");
        assertFalse(
                SupplyGate.isToolTierSupply("left_ore:-188,-2,-448"),
                "Тикет оставленной руды не supply: его гейт по маркеру кирки сохраняется");
    }
}
