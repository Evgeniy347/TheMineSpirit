package com.theminespirit.core.board;

/**
 * Исход операции доски: постановка (submit), выдача (claim).
 * {@code BACKOFF} — задание с этим id провалено недавно и до {@link BoardTuning#FAIL_BACKOFF_TICKS}
 * не переиздаётся: иначе доска выдавала бы проваленную добычу каждую пересборку.
 * {@code SPIRIT_PAUSED} — этот дух не берёт этот кейс: пауза после провала или кейс снят с него.
 * {@code BLOCKED} — задание заблокировано после предела провалов подряд, пока не изменятся условия.
 */
public enum BoardOutcome {
    ACCEPTED,
    DUPLICATE,
    BACKOFF,
    CLAIMED,
    TAKEN_BY_OTHER,
    GATE_BLOCKED,
    SPIRIT_PAUSED,
    BLOCKED,
    NOT_FOUND
}
