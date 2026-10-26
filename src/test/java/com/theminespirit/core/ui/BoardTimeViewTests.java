package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        assertTrue(view.showsMoment(), "Идущее задание обязано показывать начало");
        assertFalse(view.showsFinish(), "У идущего задания конца ещё нет");
        assertTrue(view.showsSpent(), "Идущее задание обязано показывать, сколько уже в работе");
        assertTrue(view.running(), "Затрата идущего задания подписывается «в работе», а не «затрачено»");
        assertEquals(2L, view.minutes(), "Длительность переводится в игровые минуты");
    }

    @Test
    void of_ClosedTask_ShowsFinishAndSpent() {
        BoardTimeView view = BoardTimeView.of(tile("DONE", 1200L, 3600L, 2400L));

        assertTrue(view.showsFinish(), "Закрытое задание обязано показывать конец");
        assertFalse(view.running(), "Затрата закрытого задания подписывается «затрачено»");
        assertEquals(2L, view.minutes(), "Время закрытого задания считается по его длительности");
    }

    @Test
    void of_UnstartedTask_ShowsNoTimeLines() {
        BoardTimeView view = BoardTimeView.of(tile("PENDING", 0L, 0L, 0L));

        assertFalse(view.showsMoment(), "Задание без начала не рисует строку времени");
        assertFalse(view.showsFinish(), "Задание без конца не рисует строку завершения");
        assertFalse(view.showsSpent(), "Задание без затраты не рисует строку длительности");
    }
}
