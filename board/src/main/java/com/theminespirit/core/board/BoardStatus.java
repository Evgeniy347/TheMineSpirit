package com.theminespirit.core.board;

public enum BoardStatus {
    PENDING,
    WAITING_RESOURCES,
    ASSIGNED,
    BLOCKED,
    DONE,
    FAILED,
    CANCELLED;

    public boolean open() {
        return this == PENDING || this == WAITING_RESOURCES || this == ASSIGNED;
    }
}
