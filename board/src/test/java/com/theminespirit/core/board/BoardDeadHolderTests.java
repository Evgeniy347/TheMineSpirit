package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BoardDeadHolderTests {

    private static Board boardAt(long now) {
        return new Board(() -> now);
    }

    private static BoardTicket ticket(String id, BoardCase boardCase) {
        return new BoardTicket(
                id,
                boardCase,
                Optional.empty(),
                BoardRules.priorityOf(boardCase),
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());
    }

    private static BoardTicket ticket(String id, BoardCase boardCase, UUID assignee) {
        return new BoardTicket(
                id,
                boardCase,
                Optional.of(assignee),
                BoardRules.priorityOf(boardCase),
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());
    }

    @Test
    void releaseDeadHolders_ClaimedRepairDeadHolder_ReleasesToPending() {
        Board board = boardAt(0);
        UUID dead = UUID.randomUUID();
        board.submit(ticket("repair:build_house", BoardCase.REPAIR_BUILDING));
        board.claim("repair:build_house", dead, 0);

        List<HolderRelease> released = board.releaseDeadHolders(Set.of(UUID.randomUUID()));

        assertEquals(
                List.of(new HolderRelease("repair:build_house", BoardCase.REPAIR_BUILDING, dead, false)),
                released,
                "Задание с погибшим держателем возвращается в доступные: иначе восстановление постройки висит за мёртвым духом"
                        + " При смене releaseDeadHolders_ClaimedRepairDeadHolder_ReleasesToPending проверь это утверждение.");
        assertEquals(
                Optional.of(BoardStatus.PENDING),
                board.statusOf("repair:build_house"),
                "Освобождённое задание ждёт нового держателя, а не висит назначенным"
                        + " Иначе тест releaseDeadHolders_ClaimedRepairDeadHolder_ReleasesToPending перестанет ловить эту поломку. При смене releaseDeadHolders_ClaimedRepairDeadHolder_ReleasesToPending проверь это утверждение.");
        assertEquals(
                Optional.empty(),
                board.holderOf("repair:build_house"),
                "У освобождённого задания нет держателя: новый дух заберёт его чистой выдачей"
                        + " Иначе тест releaseDeadHolders_ClaimedRepairDeadHolder_ReleasesToPending перестанет ловить эту поломку. При смене releaseDeadHolders_ClaimedRepairDeadHolder_ReleasesToPending проверь это утверждение.");
    }

    @Test
    void releaseDeadHolders_AssigneeDeadHolder_ReleasesToPending() {
        Board board = boardAt(0);
        UUID dead = UUID.randomUUID();
        board.submit(ticket("repair_tool:" + dead, BoardCase.REPAIR_TOOL, dead));
        board.claim("repair_tool:" + dead, dead, 0);

        List<HolderRelease> released = board.releaseDeadHolders(Set.of());

        assertTrue(
                released.size() == 1 && !released.get(0).activity(),
                "Задание с держателем в assignee тоже освобождается: ремонт инструмента не висит за удалённым духом"
                        + " Иначе тест releaseDeadHolders_AssigneeDeadHolder_ReleasesToPending перестанет ловить эту поломку. При смене releaseDeadHolders_AssigneeDeadHolder_ReleasesToPending проверь это утверждение.");
        assertEquals(
                Optional.of(BoardStatus.PENDING),
                board.statusOf("repair_tool:" + dead),
                "Освобождённый ремонт инструмента доступен живым духам"
                        + " Иначе тест releaseDeadHolders_AssigneeDeadHolder_ReleasesToPending перестанет ловить эту поломку. При смене releaseDeadHolders_AssigneeDeadHolder_ReleasesToPending проверь это утверждение.");
    }

    @Test
    void releaseDeadHolders_LiveHolder_KeepsAssigned() {
        Board board = boardAt(0);
        UUID live = UUID.randomUUID();
        board.submit(ticket("repair:build_house", BoardCase.REPAIR_BUILDING));
        board.claim("repair:build_house", live, 0);

        List<HolderRelease> released = board.releaseDeadHolders(Set.of(live));

        assertEquals(
                List.of(),
                released,
                "Задание живого держателя не трогается: пересборка снимает только погибших"
                        + " Иначе тест releaseDeadHolders_LiveHolder_KeepsAssigned перестанет ловить эту поломку. При смене releaseDeadHolders_LiveHolder_KeepsAssigned проверь это утверждение.");
        assertEquals(
                Optional.of(BoardStatus.ASSIGNED),
                board.statusOf("repair:build_house"),
                "Назначенное живому духу задание остаётся назначенным"
                        + " Иначе тест releaseDeadHolders_LiveHolder_KeepsAssigned перестанет ловить эту поломку. При смене releaseDeadHolders_LiveHolder_KeepsAssigned проверь это утверждение.");
    }

    @Test
    void releaseDeadHolders_UnclaimedTicket_Skipped() {
        Board board = boardAt(0);
        board.submit(ticket("repair:build_house", BoardCase.REPAIR_BUILDING));

        List<HolderRelease> released = board.releaseDeadHolders(Set.of());

        assertEquals(
                List.of(),
                released,
                "Задание без держателя не освобождается: ему не от кого освобождаться"
                        + " Иначе тест releaseDeadHolders_UnclaimedTicket_Skipped перестанет ловить эту поломку. При смене releaseDeadHolders_UnclaimedTicket_Skipped проверь это утверждение.");
        assertEquals(
                Optional.of(BoardStatus.PENDING),
                board.statusOf("repair:build_house"),
                "Свободное задание остаётся свободным"
                        + " Иначе тест releaseDeadHolders_UnclaimedTicket_Skipped перестанет ловить эту поломку. При смене releaseDeadHolders_UnclaimedTicket_Skipped проверь это утверждение.");
    }
}
