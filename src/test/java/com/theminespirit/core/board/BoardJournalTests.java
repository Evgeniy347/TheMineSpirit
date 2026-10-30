package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BoardJournalTests {
    @TempDir
    Path directory;

    private static BoardLogEntry entry(BoardLog.Event event, String id, BoardCase boardCase, int level) {
        return new BoardLogEntry(
                event, 10, id, boardCase, level, "always", 50, 65, List.of("pen_caught_animal"), null, "hash-1", 10);
    }

    private Path log() {
        return directory.resolve("theminespirit/board-log.jsonl");
    }

    @Test
    void load_DoneLine_RestoresBuildingMark() {
        BoardJournal journal = new BoardJournal(log());
        journal.enqueue(entry(BoardLog.Event.DONE, "pen", BoardCase.BUILD_PEN, 2));
        journal.flushAll();
        BoardLedger ledger = new BoardLedger();

        BoardJournal.LoadResult result = journal.load(ledger);

        assertEquals(
                1,
                result.restored(),
                "DONE-запись обязана вернуть ровно одну отметку: без этого «загон построен» "
                        + "не переживёт перезапуск");
        assertTrue(
                ledger.has(BoardCase.BUILD_PEN, 2),
                "Ledger обязан увидеть выполненный уровень 2 загона после загрузки журнала");
        assertFalse(result.failed(), "Целый журнал обязан загружаться без ошибки");
    }

    @Test
    void load_ReopenedAfterDone_MarkForgotten() {
        BoardJournal journal = new BoardJournal(log());
        journal.enqueue(entry(BoardLog.Event.DONE, "first:lighting", BoardCase.LIGHTING, 1));
        journal.enqueue(entry(BoardLog.Event.REOPENED, "first:lighting", BoardCase.LIGHTING, 1));
        journal.flushAll();
        BoardLedger ledger = new BoardLedger();

        journal.load(ledger);

        assertFalse(
                ledger.has(BoardCase.LIGHTING, 1),
                "REOPENED после DONE снимает отметку и после перезапуска: иначе журнал вернул бы старый DONE (INC-50)");
    }

    @Test
    void load_DoneThenReopened_KeepsCumulativeDoneAcrossReload() {
        BoardJournal journal = new BoardJournal(log());
        journal.enqueue(entry(BoardLog.Event.DONE, "repair:build_house", BoardCase.BUILD_HOUSE, 1));
        journal.enqueue(entry(BoardLog.Event.REOPENED, "repair:build_house", BoardCase.BUILD_HOUSE, 1));
        journal.flushAll();
        BoardLedger ledger = new BoardLedger();

        journal.load(ledger);

        assertEquals(
                1,
                ledger.doneTotal(),
                "Журнал прошлой сессии не откатывает DONE на REOPENED: иначе после перезапуска наблюдатель "
                        + "снова видит 1230→1229");
        assertEquals(
                1,
                ledger.reopenedTotal(),
                "REOPENED после перезапуска учитывается отдельно: по нему видно законный ремонт");
        journal.load(ledger);
        assertEquals(
                1,
                ledger.doneTotal(),
                "Повторная загрузка того же журнала не удваивает DONE: счётчик считается один раз на файл");
    }

    @Test
    void load_BrokenTail_TruncatesItAndKeepsPreviousMarks() throws IOException {
        BoardJournal journal = new BoardJournal(log());
        journal.enqueue(entry(BoardLog.Event.DONE, "mine", BoardCase.BUILD_MINE, 1));
        journal.flushAll();
        Files.writeString(log(), "{\"event\":\"DONE\",\"tick\":", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        BoardLedger ledger = new BoardLedger();

        BoardJournal.LoadResult result = journal.load(ledger);

        assertTrue(
                result.failed(),
                "Оборванная хвостовая строка обязана попасть в результат ошибкой, " + "а не потеряться молча");
        assertTrue(result.truncated(), "Оборванный хвост обязан быть отрезан, иначе он мешает следующей записи");
        assertTrue(
                ledger.has(BoardCase.BUILD_MINE, 1),
                "Строки до оборванного хвоста обязаны восстановиться: обрыв не отменяет выполненное");
        String content = Files.readString(log(), StandardCharsets.UTF_8);
        assertEquals(
                1,
                content.lines().filter(line -> !line.isBlank()).count(),
                "После обрезки битой строки в файле остаётся ровно одна целая запись: " + content);
    }

    @Test
    void flush_MoreLinesThanBudget_WritesOnlyBudget() {
        BoardJournal journal = new BoardJournal(log());
        for (int index = 0; index < 5; index++) {
            journal.enqueue(entry(BoardLog.Event.ASSIGNED, "t" + index, BoardCase.SUPPLY_SITE, 1));
        }

        assertEquals(
                2, journal.flush(2), "Бюджет сброса обязан ограничивать число записанных строк: журнал не грузит тик");
        assertEquals(
                3,
                journal.pending(),
                "Остальные строки ждут следующего сброса, а не теряются: за тик пишется не больше бюджета");
        assertEquals(3, journal.flushAll(), "Второй сброс обязан дописать остаток");
        assertEquals(0, journal.pending(), "После полного сброса очередь пуста");
    }

    @Test
    void flush_Counters_MatchEnqueuedAndWritten() {
        BoardJournal journal = new BoardJournal(log());
        for (int index = 0; index < 5; index++) {
            journal.enqueue(entry(BoardLog.Event.ASSIGNED, "t" + index, BoardCase.SUPPLY_SITE, 1));
        }

        assertEquals(
                5,
                journal.enqueued(),
                "Счётчик поставленных записей обязан равняться числу переходов: тест масштаба "
                        + "сверяет по нему строки журнала с переходами статусов");
        assertEquals(0, journal.written(), "До сброса на диске ничего нет, а очередь держит записи");
        journal.flush(2);
        assertEquals(
                2,
                journal.written(),
                "Счётчик записанных строк обязан расти на бюджет сброса: каждая строка — один переход");
        journal.flushAll();
        assertEquals(
                5,
                journal.written(),
                "После полного сброса записаны все поставленные строки: журнал не теряет переходы");
    }

    @Test
    void last_Limit_ReturnsNewestFirst() {
        BoardJournal journal = new BoardJournal(log());
        journal.enqueue(entry(BoardLog.Event.ASSIGNED, "old", BoardCase.SUPPLY_SITE, 1));
        journal.enqueue(entry(BoardLog.Event.DONE, "new", BoardCase.BUILD_PEN, 2));
        journal.flushAll();

        List<BoardLogEntry> entries = journal.last(1);

        assertEquals(1, entries.size(), "limit обязан ограничивать ответ: " + entries);
        assertEquals(
                "new", entries.get(0).ticketId(), "Последняя запись отдаётся первой: наблюдатель видит свежие события");
    }

    @Test
    void last_Utf8Lines_StayReadable() {
        BoardJournal journal = new BoardJournal(log());
        journal.enqueue(entry(BoardLog.Event.DONE, "загон:корова", BoardCase.BUILD_PEN, 1));
        journal.flushAll();

        assertEquals(
                "загон:корова",
                journal.last(1).get(0).ticketId(),
                "Кириллица в id задания обязана переживать запись и чтение UTF-8");
    }
}
