package com.theminespirit.core.board;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface BoardEvents {
    BoardEvents NONE = (event, ticket, holder, tick) -> {};

    void transition(BoardLog.Event event, BoardTicket ticket, @Nullable UUID holder, long tick);
}
