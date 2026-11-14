package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.RepeatLog;
import com.theminespirit.core.log.Perf;
import java.util.Objects;
import java.util.Optional;

public final class TickWatch {
    public static final long LIMIT_MILLIS = 500;
    private static final long NANOS_PER_TICK = 50_000_000L;
    private static final TickWatch DEFAULT = new TickWatch(BoardTuning.logSummaryTicks() * NANOS_PER_TICK);

    private final RepeatLog slow;

    TickWatch(long summaryNanos) {
        this.slow = new RepeatLog(summaryNanos);
    }

    public static long start() {
        return System.nanoTime();
    }

    public static void done(String operation, long startedNanos) {
        long now = System.nanoTime();
        Perf.record(operation, now - startedNanos);
        DEFAULT.observe(operation, now - startedNanos, now)
                .ifPresent(report -> LogUtils.getLogger().warn(report));
    }

    Optional<String> observe(String operation, long elapsedNanos, long nowNanos) {
        Objects.requireNonNull(operation, "Имя операции не может быть null: без него неясно, что держало поток");
        long millis = elapsedNanos / 1_000_000L;
        if (millis < LIMIT_MILLIS) {
            slow.clear(operation);
            return Optional.empty();
        }
        return slow.note(operation, millis, nowNanos)
                .map(summary -> summary.count() == 1 ? describe(operation, elapsedNanos) : summary(operation, summary));
    }

    static String describe(String operation, long elapsedNanos) {
        Objects.requireNonNull(operation, "Имя операции не может быть null: без него неясно, что держало поток");
        long millis = elapsedNanos / 1_000_000L;
        if (millis < LIMIT_MILLIS) {
            return null;
        }
        return "[tick] операция " + operation + " держала серверный поток " + millis + " мс";
    }

    private static String summary(String operation, RepeatLog.Summary repeat) {
        return "[tick] операция " + operation + " держала серверный поток " + repeat.count() + " раз за сводку: "
                + repeat.min() + "–" + repeat.max() + " мс, всего " + repeat.total() + " мс";
    }
}
