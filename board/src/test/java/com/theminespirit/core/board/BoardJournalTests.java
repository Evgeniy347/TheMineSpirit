package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
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
                        + "не переживёт перезапуск"
                        + " При смене load_DoneLine_RestoresBuildingMark проверь это утверждение.");
        assertTrue(
                ledger.has(BoardCase.BUILD_PEN, 2),
                "Ledger обязан увидеть выполненный уровень 2 загона после загрузки журнала"
                        + " Иначе тест load_DoneLine_RestoresBuildingMark перестанет ловить эту поломку. При смене load_DoneLine_RestoresBuildingMark проверь это утверждение.");
        assertFalse(
                result.failed(),
                "Целый журнал обязан загружаться без ошибки"
                        + " Иначе тест load_DoneLine_RestoresBuildingMark перестанет ловить эту поломку. При смене load_DoneLine_RestoresBuildingMark проверь это утверждение.");
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
                "REOPENED после DONE снимает отметку и после перезапуска: иначе журнал вернул бы старый DONE (INC-50)"
                        + " При смене load_ReopenedAfterDone_MarkForgotten проверь это утверждение.");
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
                        + "снова видит 1230→1229"
                        + " При смене load_DoneThenReopened_KeepsCumulativeDoneAcrossReload проверь это утверждение.");
        assertEquals(
                1,
                ledger.reopenedTotal(),
                "REOPENED после перезапуска учитывается отдельно: по нему видно законный ремонт"
                        + " Иначе тест load_DoneThenReopened_KeepsCumulativeDoneAcrossReload перестанет ловить эту поломку. При смене load_DoneThenReopened_KeepsCumulativeDoneAcrossReload проверь это утверждение.");
        journal.load(ledger);
        assertEquals(
                1,
                ledger.doneTotal(),
                "Повторная загрузка того же журнала не удваивает DONE: счётчик считается один раз на файл"
                        + " Иначе тест load_DoneThenReopened_KeepsCumulativeDoneAcrossReload перестанет ловить эту поломку. При смене load_DoneThenReopened_KeepsCumulativeDoneAcrossReload проверь это утверждение.");
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
                "Оборванная хвостовая строка обязана попасть в результат ошибкой, " + "а не потеряться молча"
                        + " Иначе тест load_BrokenTail_TruncatesItAndKeepsPreviousMarks перестанет ловить эту поломку. При смене load_BrokenTail_TruncatesItAndKeepsPreviousMarks проверь это утверждение.");
        assertTrue(
                result.truncated(),
                "Оборванный хвост обязан быть отрезан, иначе он мешает следующей записи"
                        + " При смене load_BrokenTail_TruncatesItAndKeepsPreviousMarks проверь это утверждение.");
        assertTrue(
                ledger.has(BoardCase.BUILD_MINE, 1),
                "Строки до оборванного хвоста обязаны восстановиться: обрыв не отменяет выполненное"
                        + " Иначе тест load_BrokenTail_TruncatesItAndKeepsPreviousMarks перестанет ловить эту поломку. При смене load_BrokenTail_TruncatesItAndKeepsPreviousMarks проверь это утверждение.");
        String content = Files.readString(log(), StandardCharsets.UTF_8);
        assertEquals(
                1,
                content.lines().filter(line -> !line.isBlank()).count(),
                "После обрезки битой строки в файле остаётся ровно одна целая запись: " + content
                        + " Иначе тест load_BrokenTail_TruncatesItAndKeepsPreviousMarks перестанет ловить эту поломку. При смене load_BrokenTail_TruncatesItAndKeepsPreviousMarks проверь это утверждение.");
        assertFalse(
                Files.exists(log().resolveSibling(log().getFileName() + ".truncated")),
                "Временный файл обрезки обязан исчезнуть после замены журнала: иначе на сервере остаётся "
                        + "копия всех строк и место кончается"
                        + " Иначе тест load_BrokenTail_TruncatesItAndKeepsPreviousMarks перестанет ловить эту поломку. При смене load_BrokenTail_TruncatesItAndKeepsPreviousMarks проверь это утверждение.");
    }

    @Test
    void flush_MoreLinesThanBudget_WritesOnlyBudget() {
        BoardJournal journal = new BoardJournal(log());
        for (int index = 0; index < 5; index++) {
            journal.enqueue(entry(BoardLog.Event.ASSIGNED, "t" + index, BoardCase.SUPPLY_SITE, 1));
        }

        assertEquals(
                2,
                journal.flush(2),
                "Бюджет сброса обязан ограничивать число записанных строк: журнал не грузит тик"
                        + " Иначе тест flush_MoreLinesThanBudget_WritesOnlyBudget перестанет ловить эту поломку. При смене flush_MoreLinesThanBudget_WritesOnlyBudget проверь это утверждение.");
        assertEquals(
                3,
                journal.pending(),
                "Остальные строки ждут следующего сброса, а не теряются: за тик пишется не больше бюджета"
                        + " Иначе тест flush_MoreLinesThanBudget_WritesOnlyBudget перестанет ловить эту поломку. При смене flush_MoreLinesThanBudget_WritesOnlyBudget проверь это утверждение.");
        assertEquals(
                3,
                journal.flushAll(),
                "Второй сброс обязан дописать остаток"
                        + " Иначе тест flush_MoreLinesThanBudget_WritesOnlyBudget перестанет ловить эту поломку. При смене flush_MoreLinesThanBudget_WritesOnlyBudget проверь это утверждение.");
        assertEquals(
                0,
                journal.pending(),
                "После полного сброса очередь пуста"
                        + " Иначе тест flush_MoreLinesThanBudget_WritesOnlyBudget перестанет ловить эту поломку. При смене flush_MoreLinesThanBudget_WritesOnlyBudget проверь это утверждение.");
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
                        + "сверяет по нему строки журнала с переходами статусов"
                        + " Иначе тест flush_Counters_MatchEnqueuedAndWritten перестанет ловить эту поломку. При смене flush_Counters_MatchEnqueuedAndWritten проверь это утверждение.");
        assertEquals(
                0,
                journal.written(),
                "До сброса на диске ничего нет, а очередь держит записи"
                        + " Иначе тест flush_Counters_MatchEnqueuedAndWritten перестанет ловить эту поломку. При смене flush_Counters_MatchEnqueuedAndWritten проверь это утверждение.");
        journal.flush(2);
        assertEquals(
                2,
                journal.written(),
                "Счётчик записанных строк обязан расти на бюджет сброса: каждая строка — один переход"
                        + " Иначе тест flush_Counters_MatchEnqueuedAndWritten перестанет ловить эту поломку. При смене flush_Counters_MatchEnqueuedAndWritten проверь это утверждение.");
        journal.flushAll();
        assertEquals(
                5,
                journal.written(),
                "После полного сброса записаны все поставленные строки: журнал не теряет переходы"
                        + " Иначе тест flush_Counters_MatchEnqueuedAndWritten перестанет ловить эту поломку. При смене flush_Counters_MatchEnqueuedAndWritten проверь это утверждение.");
    }

    @Test
    void last_Limit_ReturnsNewestFirst() {
        BoardJournal journal = new BoardJournal(log());
        journal.enqueue(entry(BoardLog.Event.ASSIGNED, "old", BoardCase.SUPPLY_SITE, 1));
        journal.enqueue(entry(BoardLog.Event.DONE, "new", BoardCase.BUILD_PEN, 2));
        journal.flushAll();

        List<BoardLogEntry> entries = journal.last(1);

        assertEquals(
                1,
                entries.size(),
                "limit обязан ограничивать ответ: " + entries
                        + " Иначе тест last_Limit_ReturnsNewestFirst перестанет ловить эту поломку. При смене last_Limit_ReturnsNewestFirst проверь это утверждение.");
        assertEquals(
                "new",
                entries.get(0).ticketId(),
                "Последняя запись отдаётся первой: наблюдатель видит свежие события"
                        + " Иначе тест last_Limit_ReturnsNewestFirst перестанет ловить эту поломку. При смене last_Limit_ReturnsNewestFirst проверь это утверждение.");
    }

    @Test
    void last_ManyLinesAcrossChunks_ReturnsNewestPortion() {
        BoardJournal journal = new BoardJournal(log());
        for (int index = 0; index < 1_000; index++) {
            journal.enqueue(entry(BoardLog.Event.DONE, "id-" + index, BoardCase.BUILD_PEN, 1));
        }
        journal.flushAll();

        List<BoardLogEntry> entries = journal.last(900);

        assertEquals(
                900,
                entries.size(),
                "Хвост журнала обязан собираться из нескольких блоков чтения без потери строк: " + entries.size()
                        + " Иначе тест last_ManyLinesAcrossChunks_ReturnsNewestPortion перестанет ловить эту поломку. При смене last_ManyLinesAcrossChunks_ReturnsNewestPortion проверь это утверждение.");
        assertEquals(
                "id-999",
                entries.get(0).ticketId(),
                "Хвост всё равно обязан начинаться с самой свежей записи, а не с начала файла"
                        + " Иначе тест last_ManyLinesAcrossChunks_ReturnsNewestPortion перестанет ловить эту поломку. При смене last_ManyLinesAcrossChunks_ReturnsNewestPortion проверь это утверждение.");
    }

    @Test
    void last_Utf8Lines_StayReadable() {
        BoardJournal journal = new BoardJournal(log());
        journal.enqueue(entry(BoardLog.Event.DONE, "загон:корова", BoardCase.BUILD_PEN, 1));
        journal.flushAll();

        assertEquals(
                "загон:корова",
                journal.last(1).get(0).ticketId(),
                "Кириллица в id задания обязана переживать запись и чтение UTF-8"
                        + " Иначе тест last_Utf8Lines_StayReadable перестанет ловить эту поломку. При смене last_Utf8Lines_StayReadable проверь это утверждение.");
    }

    @Test
    void load_RetiredCaseLine_SkipsItAndKeepsNeighbours() throws IOException {
        BoardJournal journal = new BoardJournal(log());
        journal.enqueue(entry(BoardLog.Event.DONE, "pen", BoardCase.BUILD_PEN, 1));
        journal.enqueue(entry(BoardLog.Event.FAILED, "undo:old", BoardCase.BUILD_PEN, 1));
        journal.enqueue(entry(BoardLog.Event.DONE, "mine", BoardCase.BUILD_MINE, 1));
        journal.flushAll();
        List<String> lines = Files.readAllLines(log(), StandardCharsets.UTF_8);
        lines.set(1, lines.get(1).replace("BUILD_PEN", "ACTIVITY_UNDO"));
        Files.writeString(log(), String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
        BoardLedger ledger = new BoardLedger();

        BoardJournal.LoadResult result = journal.load(ledger);

        assertFalse(
                result.failed(),
                "Строка удалённого кейса не считается поломкой журнала: иначе старый файл валит доску "
                        + "и на сервере нет ни доски, ни REST"
                        + " Иначе тест load_RetiredCaseLine_SkipsItAndKeepsNeighbours перестанет ловить эту поломку. При смене load_RetiredCaseLine_SkipsItAndKeepsNeighbours проверь это утверждение.");
        assertEquals(
                2,
                result.restored(),
                "Соседние записи обязаны восстановиться, а строка удалённого кейса — пропуститься"
                        + " Иначе тест load_RetiredCaseLine_SkipsItAndKeepsNeighbours перестанет ловить эту поломку. При смене load_RetiredCaseLine_SkipsItAndKeepsNeighbours проверь это утверждение.");
    }

    @Test
    void last_RetiredCaseLine_SkipsIt() throws IOException {
        BoardJournal journal = new BoardJournal(log());
        journal.enqueue(entry(BoardLog.Event.DONE, "pen", BoardCase.BUILD_PEN, 1));
        journal.enqueue(entry(BoardLog.Event.FAILED, "undo:old", BoardCase.BUILD_PEN, 1));
        journal.flushAll();
        List<String> lines = Files.readAllLines(log(), StandardCharsets.UTF_8);
        lines.set(1, lines.get(1).replace("BUILD_PEN", "ACTIVITY_UNDO"));
        Files.writeString(log(), String.join("\n", lines) + "\n", StandardCharsets.UTF_8);

        List<BoardLogEntry> entries = journal.last(10);

        assertEquals(
                1,
                entries.size(),
                "Хвост журнала не отдаёт строки удалённых кейсов: клиент и REST знают только живые кейсы"
                        + " Иначе тест last_RetiredCaseLine_SkipsIt перестанет ловить эту поломку. При смене last_RetiredCaseLine_SkipsIt проверь это утверждение.");
    }
}
