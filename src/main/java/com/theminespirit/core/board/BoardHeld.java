package com.theminespirit.core.board;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class BoardHeld {
    private BoardHeld() {}

    public static Optional<BoardTicket> of(Board board, UUID spirit) {
        Objects.requireNonNull(board, "Доска не может быть null: держатель задания читается с неё");
        Objects.requireNonNull(spirit, "spirit не может быть null: держатель задания — дух");
        List<BoardTicket> assigned = board.assignedTo(spirit);
        if (assigned.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(assigned.get(0));
    }
}
