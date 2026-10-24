package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BoardLogPlacementTests {
    @TempDir
    Path directory;

    @Test
    void placement_ChestPlaced_JournalNamesSpiritTaskPlaceReason() {
        BoardJournal journal = new BoardJournal(directory.resolve("board-log.jsonl"));
        journal.enqueue(BoardLog.placement(
                "spirit-1", "craft", "minecraft:chest", new BlockCoord(-177, 67, -474), "скрафчено с place", 50, "h"));
        journal.flushAll();

        BoardLogEntry read = journal.last(1).get(0);

        assertEquals(BoardLog.Event.NOTE, read.event(), "Установка блока вне стройки — заметка журнала (INC-54)");
        assertEquals("place:minecraft:chest", read.ticketId(), "В журнале — что поставлено");
        assertEquals("spirit-1", read.spirit(), "В журнале — кто поставил: иначе лишний сундук не разобрать");
        assertEquals(
                List.of("task=craft", "pos=-177,67,-474", "reason=скрафчено с place"),
                read.modifiers(),
                "В журнале — задача, позиция и причина установки (INC-54)");
    }
}
