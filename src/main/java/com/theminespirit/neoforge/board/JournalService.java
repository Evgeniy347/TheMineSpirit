package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardJournal;
import com.theminespirit.core.board.BoardLedger;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.BoardLogEntry;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.ConfigStamp;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

/**
 * Журнал доски рядом с конфигом сервера: {@code config/theminespirit/board-log.jsonl}.
 * Запись идёт очередью и сбрасывается бюджетом за тик; при старте {@code DONE}-записи
 * возвращаются в ledger, поэтому «постройка стоит» переживает перезапуск, а «ждёт выдачи» — нет.
 */
public final class JournalService {
    public static final int FLUSH_BUDGET = 16;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LOG_FILE = "theminespirit/board-log.jsonl";

    private final BoardJournal journal;

    public JournalService(Path path) {
        this.journal = new BoardJournal(Objects.requireNonNull(path,
                "Путь журнала не может быть null: без него запись некуда класть"));
    }

    public static JournalService inConfigDirectory() {
        return new JournalService(FMLPaths.CONFIGDIR.get().resolve(LOG_FILE));
    }

    public Path path() {
        return journal.path();
    }

    public void record(BoardLog.Event event, BoardTicket ticket, UUID holder, long tick) {
        journal.enqueue(new BoardLogEntry(event, tick, ticket.id(), ticket.boardCase(), ticket.level(),
                ticket.cause(), BoardRules.priorityOf(ticket.boardCase()), ticket.priority(),
                ticket.modifiers(), holder == null ? null : holder.toString(), ConfigStamp.current(), tick));
    }

    public int flush(int maxLines) {
        return journal.flush(maxLines);
    }

    public int flushAll() {
        return journal.flushAll();
    }

    public int pending() {
        return journal.pending();
    }

    public List<BoardLogEntry> last(int limit) {
        return journal.last(limit);
    }

    /** Восстановление ledger при старте; оборванный хвост виден в логе, а не теряется молча. */
    public int restore(BoardLedger ledger) {
        BoardJournal.LoadResult result = journal.load(ledger);
        if (result.failed()) {
            LOGGER.error("Журнал заданий '{}' строка {} не разобрана: {}{}", journal.path(),
                    result.failedLine(), result.error(), result.truncated()
                            ? " — оборванный хвост отрезан, доска поднимается без него"
                            : " — загрузка остановлена на этой строке, проверьте файл");
        }
        return result.restored();
    }
}
