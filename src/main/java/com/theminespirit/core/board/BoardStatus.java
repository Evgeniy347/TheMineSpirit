package com.theminespirit.core.board;

/** Статус задания на доске: ожидает выдачи, выдано духу, заблокировано, завершено или снято. */
public enum BoardStatus {
    PENDING,
    WAITING_RESOURCES,
    ASSIGNED,
    BLOCKED,
    DONE,
    FAILED,
    CANCELLED
}
