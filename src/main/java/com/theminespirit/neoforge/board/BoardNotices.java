package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BlockNoteLog;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.BoardLogEntry;
import com.theminespirit.core.board.BoardNoticeText;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.BoardWhy;
import com.theminespirit.core.board.PendingStall;
import com.theminespirit.core.board.StatusNote;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketTarget;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;

final class BoardNotices {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final PendingStall pending = new PendingStall();
    private final BlockNoteLog blockNotes = new BlockNoteLog(BoardTuning.logSummaryTicks());

    void blocks(Board board, JournalService journal, StockView stock, long now) {
        Set<String> alive = new HashSet<>();
        for (BoardTicket ticket : board.all()) {
            alive.add(ticket.id());
        }
        blockNotes.retain(alive);
        for (BoardLogEntry entry : board.drainBlocks()) {
            journal.recordEntry(entry);
            blockNotes.observe(entry, now).ifPresent(summary -> {
                if (summary.changed()) {
                    LOGGER.info(BoardNoticeText.line(board, entry, stock, now));
                    return;
                }
                LOGGER.info(
                        "[board] сводка: задание {} {} повторялось {} раз за окно",
                        entry.ticketId(),
                        entry.event(),
                        summary.count());
            });
        }
    }

    void assigned(Board board, BoardTicket ticket, TicketTarget target, String chosen) {
        String details = "цель=" + target.target() + " предмет=" + text(target.supplyItem()) + " количество="
                + target.count() + " попыток=" + board.ticketFailures(ticket.id());
        LOGGER.info(StatusNote.line(
                BoardLog.Event.ASSIGNED,
                ticket.id(),
                ticket.boardCase(),
                board.ticketFailures(ticket.id()),
                board.waitNote(ticket.id()).orElse(null),
                chosen,
                details));
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    void watchPending(Board board, StockView stock, long now) {
        Set<String> waiting = new HashSet<>();
        for (BoardTicket ticket : board.all()) {
            if (board.statusOf(ticket.id()).orElse(null) != BoardStatus.PENDING) {
                continue;
            }
            waiting.add(ticket.id());
            String reason = BoardWhy.waitReason(board, ticket, stock, now).orElse(null);
            pending.observe(ticket.id(), reason, now);
        }
        pending.retain(waiting);
        pending.summary(now).ifPresent(LOGGER::warn);
    }
}
