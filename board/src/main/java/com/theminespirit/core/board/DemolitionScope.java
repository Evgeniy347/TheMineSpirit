package com.theminespirit.core.board;

import com.theminespirit.core.board.graph.GraphWorldEvents;
import java.util.Objects;

public enum DemolitionScope {
    STRAYS,
    FLOATING,
    BOTH;

    public static DemolitionScope of(String cause) {
        Objects.requireNonNull(cause, "Причина уборки не может быть null: по ней выбирается очередь");
        if (cause.contains(GraphWorldEvents.CLEAR_FLOATING)) {
            return FLOATING;
        }
        if (cause.contains(GraphWorldEvents.DEMOLISH_STRAY)) {
            return STRAYS;
        }
        return BOTH;
    }
}
