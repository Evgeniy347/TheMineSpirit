package com.theminespirit.core.log;

import java.util.Objects;

public final class BlockAction {
    public static final String REFUSE = "refuse";

    private BlockAction() {}

    public static String of(LogCause cause, String attempted) {
        Objects.requireNonNull(cause, "Причина изменения блока не может быть null: по ней читают журнал");
        Objects.requireNonNull(attempted, "Попытка изменения блока не может быть null: без неё строку не опознать");
        return cause == LogCause.REFUSE ? REFUSE : attempted;
    }
}
