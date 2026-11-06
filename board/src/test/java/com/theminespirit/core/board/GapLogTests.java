package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class GapLogTests {
    @Test
    void changed_SameMissingList_ReportedOnlyOnce() {
        BoardCase boardCase = BoardCase.BUILD_STREETS;

        assertTrue(
                GapLog.changed(boardCase, List.of("(-1,70,-1) oak_planks")),
                "Первое расхождение плана обязано попасть в лог: без него не видно, чего не хватает");
        assertFalse(
                GapLog.changed(boardCase, List.of("(-1,70,-1) oak_planks")),
                "Тот же список недостающих блоков не пишется каждые 5 секунд: [layout] спамил 164 строки");
        assertTrue(
                GapLog.changed(boardCase, List.of("(-2,70,-1) oak_planks")),
                "Изменившийся список недостающих блоков обязан снова попасть в лог");
    }
}
