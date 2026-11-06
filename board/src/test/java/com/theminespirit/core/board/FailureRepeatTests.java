package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class FailureRepeatTests {
    @Test
    void note_FirstReason_IsPrinted() {
        FailureRepeat repeat = new FailureRepeat(6000);

        assertTrue(
                repeat.note("rescue:1", "путь в тупик", 10).orElseThrow().changed(),
                "Первый провал задания печатается сразу: иначе причина теряется за окном сводки");
    }

    @Test
    void note_SameReasonNextAttempt_IsSilent() {
        FailureRepeat repeat = new FailureRepeat(6000);
        repeat.note("rescue:1", "путь в тупик", 10);

        assertEquals(
                Optional.empty(),
                repeat.note("rescue:1", "путь в тупик", 20),
                "Тот же провал на следующей попытке не печатается: иначе rescue заливает лог каждую минуту");
    }

    @Test
    void note_SameReasonAfterStatusFlip_IsStillSilent() {
        FailureRepeat repeat = new FailureRepeat(6000);
        repeat.note("rescue:1", "путь в тупик", 10);

        assertEquals(
                Optional.empty(),
                repeat.note("rescue:1", "путь в тупик", 20),
                "Статус задания (FAILED/BLOCKED) не входит в причину: смена статуса не должна повторять провал");
    }

    @Test
    void note_ChangedReason_IsPrintedImmediately() {
        FailureRepeat repeat = new FailureRepeat(6000);
        repeat.note("rescue:1", "путь в тупик", 10);

        assertTrue(
                repeat.note("rescue:1", "нет материала столба", 20)
                        .orElseThrow()
                        .changed(),
                "Смена причины — новый провал: её нельзя прятать за сводкой старой");
    }

    @Test
    void note_AfterWindow_ReturnsSummaryOfRepeats() {
        FailureRepeat repeat = new FailureRepeat(100);
        repeat.note("rescue:1", "путь в тупик", 10);

        FailureRepeat.Note note = repeat.note("rescue:1", "путь в тупик", 200).orElseThrow();

        assertFalse(note.changed(), "Сводка повторов не притворяется новым провалом");
        assertEquals(2, note.count(), "Сводка называет число провалов за окно");
        assertEquals("путь в тупик", note.reason(), "Сводка называет причину: по ней решают, что чинить");
    }

    @Test
    void note_OtherTicket_IsPrintedSeparately() {
        FailureRepeat repeat = new FailureRepeat(6000);
        repeat.note("rescue:1", "путь в тупик", 10);

        assertTrue(
                repeat.note("rescue:2", "путь в тупик", 20).orElseThrow().changed(),
                "Провалы разных заданий считаются отдельно: причина общая, но задания разные");
    }
}
