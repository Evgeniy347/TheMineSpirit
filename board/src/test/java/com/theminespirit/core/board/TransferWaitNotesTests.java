package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class TransferWaitNotesTests {
    @Test
    void observe_SameState_WritesOnce() {
        TransferWaitNotes notes = new TransferWaitNotes();
        String state = "носильщики: spirit-2, spirit-3; у крафтера нет ингредиентов";

        Optional<String> first = notes.observe("transfer:repair:build_house:jungle_log", state);
        Optional<String> second = notes.observe("transfer:repair:build_house:jungle_log", state);

        assertTrue(first.isPresent(), "Первое наблюдение состояния передачи пишется: причина ожидания нужна в логе");
        assertEquals(
                Optional.empty(),
                second,
                "Повтор того же состояния не пишется каждый тик: 203 строки в минуту — шум, а не диагностика (N3)");
    }

    @Test
    void observe_StateChanged_WritesWithReason() {
        TransferWaitNotes notes = new TransferWaitNotes();

        notes.observe("transfer:repair:build_house:jungle_log", "носильщики: spirit-2");
        Optional<String> changed =
                notes.observe("transfer:repair:build_house:jungle_log", "носильщики: spirit-2, spirit-3");

        assertTrue(
                changed.orElseThrow().contains("носильщики: spirit-2, spirit-3"),
                "Смена состояния передачи пишется с причиной: по ней видно, кого ждёт крафтер");
    }
}
