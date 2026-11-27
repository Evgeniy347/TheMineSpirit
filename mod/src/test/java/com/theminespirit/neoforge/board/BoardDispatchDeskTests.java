package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.BoardTuning;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BoardDispatchDeskTests {
    @Test
    void issueAll_IdleSpirit_HasNoEndlessSenseFallback() throws IOException {
        String dispatch =
                Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/board/BoardDispatchDesk.java"));
        String activities =
                Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/board/BoardActivityDesk.java"));

        assertFalse(
                dispatch.contains("fillIdle") || activities.contains("idle_sense") || activities.contains("fillIdle"),
                "Свободный дух не получает бесконечный ACTIVITY_SENSE каждые пять секунд: иначе осмотр "
                        + "подменяет реальную работу и никогда не заканчивается. При смене "
                        + "issueAll_IdleSpirit_HasNoEndlessSenseFallback проверь BoardDispatchDesk.issueAll и живой spirit-6.");
    }

    @Test
    void due_BeforeRetryInterval_DoesNotRescanQueue() {
        assertFalse(
                BoardDispatchDesk.due(10, 0),
                "До истечения интервала доска не пересматривает очередь: иначе pending-тикеты будут "
                        + "каждый тик тратить бюджет выдачи. При смене due_BeforeRetryInterval_DoesNotRescanQueue "
                        + "проверь BoardTuning.dispatchRetryTicks и BoardDispatchDesk.tick.");
    }

    @Test
    void due_AfterRetryInterval_RescansPendingQueue() {
        long interval = BoardTuning.dispatchRetryTicks();

        assertTrue(
                BoardDispatchDesk.due(interval, 0),
                "После интервала pending-очередь пересматривается даже без нового билета: иначе отмена "
                        + "чужой задачи оставляет грядку без исполнителя навсегда. При смене "
                        + "due_AfterRetryInterval_RescansPendingQueue проверь живой graph:building:wheat_field "
                        + "после отмены graph:craft_oak_planks.");
    }
}
