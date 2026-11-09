package com.theminespirit.core.log;

import java.util.Locale;

public enum LogCause {
    PLACE,
    BREAK,
    RESTORE,
    REFUSE,
    DONE,
    FAILED,
    BLOCKED,
    UNBLOCKED,
    CANCELLED,
    REOPENED,
    DETACHED,
    CHOSEN,
    INFEASIBLE,
    NO_PATH,
    NO_MATERIAL,
    NO_TOOL,
    OUT_OF_REACH,
    CHUNK_NOT_LOADED,
    NO_SUPPORT,
    OCCUPIED,
    PROTECTED,
    TIMEOUT,
    TARGET_GONE,
    DEATH,
    RESPAWN,
    STUCK,
    RESCUE_START,
    RESCUE_DONE,
    RESCUE_FAILED,
    TAKE,
    DEPOSIT,
    PICKUP,
    DROP,
    CRAFT,
    CONSUME,
    TRANSFER,
    VERSION,
    RESTART,
    QUEUE_OVERFLOW,
    UNKNOWN;

    private final String code;

    LogCause() {
        this.code = name().toLowerCase(Locale.ROOT);
    }

    public String code() {
        return code;
    }
}
