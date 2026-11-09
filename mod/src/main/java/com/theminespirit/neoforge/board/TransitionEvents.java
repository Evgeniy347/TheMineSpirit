package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardEvents;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTransitions;
import com.theminespirit.neoforge.log.SpiritLog;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class TransitionEvents implements BoardEvents {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final JournalService journal;
    private final ServerLevel level;
    private Board board;

    TransitionEvents(JournalService journal, ServerLevel level) {
        this.journal = Objects.requireNonNull(journal, "Журнал не может быть null: переходы пишутся в него");
        this.level = Objects.requireNonNull(level, "Уровень не может быть null: по нему находится дух задания");
    }

    static Board create(LongSupplier now, JournalService journal, ServerLevel level) {
        TransitionEvents events = new TransitionEvents(journal, level);
        Board board = new Board(now, events);
        events.bind(board);
        return board;
    }

    private void bind(Board board) {
        this.board = Objects.requireNonNull(board, "Доска не может быть null: по ней считаются попытки перехода");
    }

    @Override
    public void transition(BoardLog.Event event, BoardTicket ticket, @Nullable UUID holder, long tick) {
        journal.record(event, ticket, holder, tick);
        SpiritLog.taskTransition(level, event, ticket, holder, tick);
        if (board == null) {
            return;
        }
        BoardTransitions.line(
                        event, ticket, board.ticketFailures(ticket.id()), holder == null ? null : holder.toString())
                .ifPresent(LOGGER::info);
    }
}
