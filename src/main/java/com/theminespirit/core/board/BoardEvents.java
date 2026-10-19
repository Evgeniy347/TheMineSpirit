package com.theminespirit.core.board;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Слушатель переходов доски: адаптер пишет по нему строку журнала. Ядро не знает,
 * куда идёт запись; без слушателя ({@link #NONE}) доска работает как очередь.
 */
@FunctionalInterface
public interface BoardEvents {
    BoardEvents NONE = (event, ticket, holder, tick) -> {};

    void transition(BoardLog.Event event, BoardTicket ticket, @Nullable UUID holder, long tick);
}
