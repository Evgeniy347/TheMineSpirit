package com.theminespirit.core.board;

/** Статус задания на доске: ожидает выдачи, выдано духу, завершено или снято. */
public enum BoardStatus {
    PENDING,
    WAITING_RESOURCES,
    ASSIGNED,
    DONE,
    FAILED,
    CANCELLED
}
