package com.theminespirit.core.board;

final class BlockedRestore {
    private BlockedRestore() {}

    static BoardStatus status(BoardState state, BoardState.Entry entry) {
        if (entry.status() != BoardStatus.BLOCKED) {
            return entry.status();
        }
        boolean typed = state.blocked().stream()
                .anyMatch(blocked -> blocked.ticketId().equals(entry.ticket().id()) && blocked.block() != null);
        return typed ? BoardStatus.BLOCKED : BoardStatus.PENDING;
    }
}
