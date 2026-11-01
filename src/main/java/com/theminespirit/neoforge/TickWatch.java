package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import java.util.Objects;

public final class TickWatch {
    public static final long LIMIT_MILLIS = 500;

    private TickWatch() {}

    public static long start() {
        return System.nanoTime();
    }

    public static void done(String operation, long startedNanos) {
        String report = describe(operation, System.nanoTime() - startedNanos);
        if (report != null) {
            LogUtils.getLogger().warn(report);
        }
    }

    static String describe(String operation, long elapsedNanos) {
        Objects.requireNonNull(operation, "Имя операции не может быть null: без него неясно, что держало поток");
        long millis = elapsedNanos / 1_000_000L;
        if (millis < LIMIT_MILLIS) {
            return null;
        }
        return "[tick] операция " + operation + " держала серверный поток " + millis + " мс";
    }
}
