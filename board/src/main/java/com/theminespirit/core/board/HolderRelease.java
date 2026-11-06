package com.theminespirit.core.board;

import java.util.Objects;
import java.util.UUID;

public record HolderRelease(String ticketId, BoardCase boardCase, UUID holder, boolean activity) {
    public HolderRelease {
        Objects.requireNonNull(
                ticketId, "ticketId освобождения не может быть null: без id непонятно, какое задание вернулось");
        Objects.requireNonNull(boardCase, "Кейс освобождения не может быть null: без него запись журнала не описать");
        Objects.requireNonNull(holder, "holder освобождения не может быть null: погибший держатель известен всегда");
    }
}
