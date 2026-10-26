package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        List<Board.HolderRelease> released = board.releaseDeadHolders(Set.of(UUID.randomUUID()));

        assertEquals(
                List.of(new Board.HolderRelease("repair:build_house", BoardCase.REPAIR_BUILDING, dead, false)),
                released,
                "Задание с погибшим держателем возвращается в доступные: иначе восстановление постройки висит за мёртвым духом");
        assertEquals(
                Optional.of(BoardStatus.PENDING),
                board.statusOf("repair:build_house"),
                "Освобождённое задание ждёт нового держателя, а не висит назначенным");
        assertEquals(
                Optional.empty(),
                board.holderOf("repair:build_house"),
                "У освобождённого задания нет держателя: новый дух заберёт его чистой выдачей");
    }

    @Test
    void releaseDeadHolders_AssigneeDeadHolder_ReleasesToPending() {
        Board board = boardAt(0);
        UUID dead = UUID.randomUUID();
        board.submit(ticket("repair_tool:" + dead, BoardCase.REPAIR_TOOL, dead));
        board.claim("repair_tool:" + dead, dead, 0);

        List<Board.HolderRelease> released = board.releaseDeadHolders(Set.of());

        assertTrue(
                released.size() == 1 && !released.get(0).activity(),
                "Задание с держателем в assignee тоже освобождается: ремонт инструмента не висит за удалённым духом");
        assertEquals(
                Optional.of(BoardStatus.PENDING),
                board.statusOf("repair_tool:" + dead),
                "Освобождённый ремонт инструмента доступен живым духам");
    }

    @Test
    void releaseDeadHolders_LiveHolder_KeepsAssigned() {
        Board board = boardAt(0);
        UUID live = UUID.randomUUID();
        board.submit(ticket("repair:build_house", BoardCase.REPAIR_BUILDING));
        board.claim("repair:build_house", live, 0);

        List<Board.HolderRelease> released = board.releaseDeadHolders(Set.of(live));

        assertEquals(List.of(), released, "Задание живого держателя не трогается: пересборка снимает только погибших");
        assertEquals(
                Optional.of(BoardStatus.ASSIGNED),
                board.statusOf("repair:build_house"),
                "Назначенное живому духу задание остаётся назначенным");
    }

    @Test
    void releaseDeadHolders_UnclaimedTicket_Skipped() {
        Board board = boardAt(0);
        board.submit(ticket("repair:build_house", BoardCase.REPAIR_BUILDING));

        List<Board.HolderRelease> released = board.releaseDeadHolders(Set.of());

        assertEquals(List.of(), released, "Задание без держателя не освобождается: ему не от кого освобождаться");
        assertEquals(
                Optional.of(BoardStatus.PENDING),
                board.statusOf("repair:build_house"),
                "Свободное задание остаётся свободным");
    }

    @Test
    void releaseDeadHolders_DeadActivity_Vacated() {
        Board board = boardAt(0);
        UUID dead = UUID.randomUUID();
        board.occupy(dead, BoardCase.ACTIVITY_CHOP, 0);

        List<Board.HolderRelease> released = board.releaseDeadHolders(Set.of(UUID.randomUUID()));

        assertEquals(
                List.of(new Board.HolderRelease(BoardActivities.idOf(dead), BoardCase.ACTIVITY_CHOP, dead, true)),
                released,
                "Занятие погибшего духа снимается: иначе activity:<дух> висит и занимает место в выдаче");
        assertEquals(Optional.empty(), board.occupation(dead), "У погибшего духа не остаётся занятия на доске");
    }

    @Test
    void releaseDeadHolders_LiveActivity_Kept() {
        Board board = boardAt(0);
        UUID live = UUID.randomUUID();
        board.occupy(live, BoardCase.ACTIVITY_CHOP, 0);

        List<Board.HolderRelease> released = board.releaseDeadHolders(Set.of(live));

        assertEquals(List.of(), released, "Занятие живого духа не снимается пересборкой");
        assertEquals(
                Optional.of(BoardCase.ACTIVITY_CHOP), board.occupation(live), "Занятие живого духа остаётся на доске");
    }
}
