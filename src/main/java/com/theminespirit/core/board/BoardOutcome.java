package com.theminespirit.core.board;

/**
 * Исход операции доски: постановка (submit), выдача (claim).
 * {@code BACKOFF} — задание с этим id провалено недавно и до {@link BoardTuning#FAIL_BACKOFF_TICKS}
 * не переиздаётся: иначе доска выдавала бы проваленную добычу каждую пересборку.
 */
public enum BoardOutcome {
    ACCEPTED,
    DUPLICATE,
    BACKOFF,
    CLAIMED,
    TAKEN_BY_OTHER,
    GATE_BLOCKED,
    NOT_FOUND
}
