package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BlockNoteLogTests {
    private static BoardLogEntry entry(BoardLog.Event event, String ticketId, List<String> modifiers) {
        return new BoardLogEntry(
                event, 10, ticketId, BoardCase.CRAFT_STONE_SHOVEL, 1, "graph", 10, 10, modifiers, null, "cfg", 10);
    }

    @Test
    void observe_FirstBlock_IsPrinted() {
        BlockNoteLog log = new BlockNoteLog(6000);

        ChangeSummary.Summary summary = log.observe(
                        entry(BoardLog.Event.BLOCKED, "craft:x", List.of("failures=5", "last=нет пути")), 10)
                .orElseThrow();

        assertTrue(
                summary.changed(), "Первая блокировка задания печатается сразу: иначе о ней узнают только из сводки");
    }

    @Test
    void observe_RepeatedBlockWithSameCause_IsSilent() {
        BlockNoteLog log = new BlockNoteLog(6000);
        log.observe(entry(BoardLog.Event.BLOCKED, "craft:x", List.of("failures=5", "last=нет пути", "history=a")), 10);

        assertEquals(
                Optional.empty(),
                log.observe(
                        entry(
                                BoardLog.Event.BLOCKED,
                                "craft:x",
                                List.of("failures=5", "last=нет пути", "history=a; b")),
                        20),
                "Повторная блокировка по той же причине не печатается: меняющиеся failures/history — не новая причина");
    }

    @Test
    void observe_BlockUnblockCycle_DoesNotPrintEachCycle() {
        BlockNoteLog log = new BlockNoteLog(6000);
        log.observe(entry(BoardLog.Event.BLOCKED, "craft:x", List.of("failures=5", "last=нет пути")), 10);
        log.observe(entry(BoardLog.Event.UNBLOCKED, "craft:x", List.of("failures=0", "changed=coal:2->1")), 20);

        assertEquals(
                Optional.empty(),
                log.observe(entry(BoardLog.Event.BLOCKED, "craft:x", List.of("failures=5", "last=нет пути")), 30),
                "Цикл BLOCKED→UNBLOCKED→BLOCKED с той же причиной не печатается снова: каналы блокировки и "
                        + "разблокировки помнят своё состояние");
    }

    @Test
    void observe_ChangedCause_IsPrintedImmediately() {
        BlockNoteLog log = new BlockNoteLog(6000);
        log.observe(entry(BoardLog.Event.BLOCKED, "craft:x", List.of("failures=5", "last=нет пути")), 10);

        assertTrue(
                log.observe(entry(BoardLog.Event.BLOCKED, "craft:x", List.of("failures=5", "last=нет инструмента")), 20)
                        .orElseThrow()
                        .changed(),
                "Смена причины блокировки печатается сразу: иначе отладка идёт по старой причине");
    }

    @Test
    void observe_AfterWindow_ReturnsSummary() {
        BlockNoteLog log = new BlockNoteLog(100);
        log.observe(entry(BoardLog.Event.BLOCKED, "craft:x", List.of("last=нет пути")), 10);
        log.observe(entry(BoardLog.Event.UNBLOCKED, "craft:x", List.of("changed=coal:2->1")), 20);

        ChangeSummary.Summary summary = log.observe(
                        entry(BoardLog.Event.BLOCKED, "craft:x", List.of("last=нет пути")), 200)
                .orElseThrow();

        assertFalse(summary.changed(), "Сводка повторов помечена повтором, а не новой блокировкой");
        assertEquals(2, summary.count(), "Сводка называет число повторов канала блокировки за окно");
    }

    @Test
    void retain_ForgottenTicket_StartsChangedAgain() {
        BlockNoteLog log = new BlockNoteLog(6000);
        log.observe(entry(BoardLog.Event.BLOCKED, "craft:x", List.of("last=нет пути")), 10);
        log.retain(Set.of("other"));

        assertTrue(
                log.observe(entry(BoardLog.Event.BLOCKED, "craft:x", List.of("last=нет пути")), 20)
                        .orElseThrow()
                        .changed(),
                "Задание, ушедшее с доски, забыто: вернувшись, его блокировка снова первая");
    }
}
