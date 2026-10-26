package com.theminespirit.core.board;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public final class SourceGuard {
    @FunctionalInterface
    public interface Report {
        void failed(String source, RuntimeException error, boolean repeated);
    }

    private final Report report;
    private final Map<String, String> lastErrors = new HashMap<>();
    private boolean failedRound;

    public SourceGuard(Report report) {
        this.report = Objects.requireNonNull(report, "Журнал сбоев источников не может быть null: сбой нельзя глотать");
    }

    public void startRound() {
        failedRound = false;
    }

    public boolean failedRound() {
        return failedRound;
    }

    public void run(String source, Runnable work) {
        call(
                source,
                () -> {
                    work.run();
                    return Boolean.TRUE;
                },
                Boolean.FALSE);
    }

    public <T> T call(String source, Supplier<T> work, T fallback) {
        try {
            T result = work.get();
            lastErrors.remove(source);
            return result;
        } catch (RuntimeException error) {
            failedRound = true;
            String message = String.valueOf(error.getMessage());
            boolean repeated = message.equals(lastErrors.put(source, message));
            report.failed(source, error, repeated);
            return fallback;
        }
    }
}
