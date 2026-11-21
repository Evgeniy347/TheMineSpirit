package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.ui.api.BoardTileGroup;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BoardTimeViewTests {
    private static BoardTile tile(String status, long startedAt, long finishedAt, long durationTicks) {
        return new BoardTile(
                "board-1",
                "SUPPLY_SITE",
                1,
                10,
                "Тихон",
                status,
                BoardTileGroup.ACTIVE,
                Map.of(),
                List.of(),
                0L,
                startedAt,
                finishedAt,
                durationTicks);
    }

    @Test
    void of_RunningTask_ShowsStartAndSpentWithoutFinish() {
        BoardTimeView view = BoardTimeView.of(tile("ASSIGNED", 1200L, 0L, 2400L));

        assertTrue(
                view.showsMoment(),
                "Идущее задание обязано показывать начало"
                        + " Иначе тест of_RunningTask_ShowsStartAndSpentWithoutFinish перестанет ловить эту поломку. При смене of_RunningTask_ShowsStartAndSpentWithoutFinish проверь это утверждение.");
        assertFalse(
                view.showsFinish(),
                "У идущего задания конца ещё нет"
                        + " Иначе тест of_RunningTask_ShowsStartAndSpentWithoutFinish перестанет ловить эту поломку. При смене of_RunningTask_ShowsStartAndSpentWithoutFinish проверь это утверждение.");
        assertTrue(
                view.showsSpent(),
                "Идущее задание обязано показывать, сколько уже в работе"
                        + " Иначе тест of_RunningTask_ShowsStartAndSpentWithoutFinish перестанет ловить эту поломку. При смене of_RunningTask_ShowsStartAndSpentWithoutFinish проверь это утверждение.");
        assertTrue(
                view.running(),
                "Затрата идущего задания подписывается «в работе», а не «затрачено»"
                        + " Иначе тест of_RunningTask_ShowsStartAndSpentWithoutFinish перестанет ловить эту поломку. При смене of_RunningTask_ShowsStartAndSpentWithoutFinish проверь это утверждение.");
        assertEquals(
                2L,
                view.minutes(),
                "Длительность переводится в игровые минуты"
                        + " Иначе тест of_RunningTask_ShowsStartAndSpentWithoutFinish перестанет ловить эту поломку. При смене of_RunningTask_ShowsStartAndSpentWithoutFinish проверь это утверждение.");
    }

    @Test
    void of_ClosedTask_ShowsFinishAndSpent() {
        BoardTimeView view = BoardTimeView.of(tile("DONE", 1200L, 3600L, 2400L));

        assertTrue(
                view.showsFinish(),
                "Закрытое задание обязано показывать конец"
                        + " Иначе тест of_ClosedTask_ShowsFinishAndSpent перестанет ловить эту поломку. При смене of_ClosedTask_ShowsFinishAndSpent проверь это утверждение.");
        assertFalse(
                view.running(),
                "Затрата закрытого задания подписывается «затрачено»"
                        + " Иначе тест of_ClosedTask_ShowsFinishAndSpent перестанет ловить эту поломку. При смене of_ClosedTask_ShowsFinishAndSpent проверь это утверждение.");
        assertEquals(
                2L,
                view.minutes(),
                "Время закрытого задания считается по его длительности"
                        + " Иначе тест of_ClosedTask_ShowsFinishAndSpent перестанет ловить эту поломку. При смене of_ClosedTask_ShowsFinishAndSpent проверь это утверждение.");
    }

    @Test
    void of_UnstartedTask_ShowsNoTimeLines() {
        BoardTimeView view = BoardTimeView.of(tile("PENDING", 0L, 0L, 0L));

        assertFalse(
                view.showsMoment(),
                "Задание без начала не рисует строку времени"
                        + " Иначе тест of_UnstartedTask_ShowsNoTimeLines перестанет ловить эту поломку. При смене of_UnstartedTask_ShowsNoTimeLines проверь это утверждение.");
        assertFalse(
                view.showsFinish(),
                "Задание без конца не рисует строку завершения"
                        + " Иначе тест of_UnstartedTask_ShowsNoTimeLines перестанет ловить эту поломку. При смене of_UnstartedTask_ShowsNoTimeLines проверь это утверждение.");
        assertFalse(
                view.showsSpent(),
                "Задание без затраты не рисует строку длительности"
                        + " Иначе тест of_UnstartedTask_ShowsNoTimeLines перестанет ловить эту поломку. При смене of_UnstartedTask_ShowsNoTimeLines проверь это утверждение.");
    }
}
