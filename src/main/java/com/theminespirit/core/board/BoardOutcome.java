package com.theminespirit.core.board;

/** Исход операции доски: постановка (submit), выдача (claim). */
public enum BoardOutcome {
    ACCEPTED,
    DUPLICATE,
    EXPIRED,
    CLAIMED,
    TAKEN_BY_OTHER,
    GATE_BLOCKED,
    NOT_FOUND
}
