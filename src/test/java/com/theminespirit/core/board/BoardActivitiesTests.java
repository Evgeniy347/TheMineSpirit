package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.intellect.ActivityId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BoardActivitiesTests {
    private static final UUID SPIRIT = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static int lowestQueuePriority() {
        int lowest = Integer.MAX_VALUE;
        for (BoardCase boardCase : BoardCase.values()) {
            if (!BoardActivities.isActivity(boardCase)) {
                lowest = Math.min(lowest, BoardRules.priorityOf(boardCase));
            }
        }
        return lowest;
    }

    @Test
    void occupy_Activity_ShownAsAssignedTicketOfSpirit() {
        Board board = new Board(() -> 100);

        board.occupy(SPIRIT, BoardCase.ACTIVITY_CHOP, 100);

        String id = BoardActivities.idOf(SPIRIT);
        assertEquals(
                Optional.of(BoardStatus.ASSIGNED),
                board.statusOf(id),
                "Занятие духа видно на доске активным заданием: иначе игрок не знает, чем занят дух");
        assertEquals(Optional.of(SPIRIT), board.holderOf(id), "Держатель занятия — сам дух");
        assertEquals(
                Optional.of(BoardCase.ACTIVITY_CHOP), board.occupation(SPIRIT), "Занятие читается кейсом ACTIVITY_*");
    }

    @Test
    void occupy_OtherActivity_ReplacesSingleTicket() {
        Board board = new Board(() -> 100);
        board.occupy(SPIRIT, BoardCase.ACTIVITY_CHOP, 100);

        board.occupy(SPIRIT, BoardCase.ACTIVITY_REST, 100);

        assertEquals(1, board.all().size(), "У духа одно задание-занятие: смена занятия заменяет его, а не копит");
        assertEquals(Optional.of(BoardCase.ACTIVITY_REST), board.occupation(SPIRIT), "Показано новое занятие");
    }

    @Test
    void occupy_Activity_NotCountedAsBusyNorClaimableNorStored() {
        Board board = new Board(() -> 100);
        board.occupy(SPIRIT, BoardCase.ACTIVITY_CRAFT, 100);

        assertEquals(
                Map.of(),
                board.assignedCounts(),
                "Занятие не держит духа для доски: иначе задание доски не вытеснило бы фоновое занятие");
        assertEquals(List.of(), board.assignedTo(SPIRIT), "Занятие не закрывается как задание доски");
        assertEquals(List.of(), board.claimable(new StockView(Map.of()), 100), "Занятие не выдаётся из очереди");
        assertEquals(
                0,
                board.snapshot().entries().size(),
                "Занятие — живой факт, на диск не пишется и не восстанавливается");
    }

    @Test
    void vacate_Occupied_RemovesTicket() {
        Board board = new Board(() -> 100);
        board.occupy(SPIRIT, BoardCase.ACTIVITY_SMELT, 100);

        assertTrue(board.vacate(SPIRIT), "Снятие существующего занятия сообщает успех");
        assertFalse(board.vacate(SPIRIT), "Повторное снятие — нечего снимать");
        assertEquals(List.of(), board.all(), "Снятое занятие с доски исчезает");
    }

    @Test
    void occupy_QueueCase_Rejected() {
        Board board = new Board(() -> 100);

        assertThrows(
                IllegalArgumentException.class,
                () -> board.occupy(SPIRIT, BoardCase.BUILD_HOUSE, 100),
                "Занятием показывается только ACTIVITY_*: кейс очереди так подменил бы выдачу");
    }

    @Test
    void caseOf_EveryIntellectActivity_HasActivityCaseBelowQueue() {
        for (ActivityId activity : ActivityId.values()) {
            BoardCase boardCase = BoardActivities.caseOf(activity);

            assertTrue(
                    BoardActivities.isActivity(boardCase), "Занятие " + activity + " показывается кейсом ACTIVITY_*");
            assertTrue(
                    BoardRules.priorityOf(boardCase) < lowestQueuePriority(),
                    "Фоновое занятие " + activity + " ниже любого задания доски: одна шкала приоритета, "
                            + "задание доски вытесняет занятие");
        }
    }
}
