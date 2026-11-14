package com.theminespirit.core.log;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class Perf {
    private static final Map<String, Stat> STATS = new ConcurrentHashMap<>();
    private static final long NANOS_PER_MILLI = 1_000_000L;

    private Perf() {}

    public static final class Span implements AutoCloseable {
        private final String name;
        private final long started;

        private Span(String name) {
            this.name = name;
            this.started = System.nanoTime();
        }

        @Override
        public void close() {
            record(name, System.nanoTime() - started);
        }
    }

    public static Span span(String name) {
        Objects.requireNonNull(name, "Имя участка не может быть null: без него замер не к чему отнести");
        return new Span(name);
    }

    public static void time(String name, Runnable block) {
        Span span = new Span(name);
        try {
            block.run();
        } finally {
            span.close();
        }
    }

    public static void record(String name, long nanos) {
        Objects.requireNonNull(name, "Имя участка не может быть null: без него замер не к чему отнести");
        STATS.computeIfAbsent(name, ignored -> new Stat()).add(nanos);
    }

    public static void since(String name, long startedNanos) {
        record(name, System.nanoTime() - startedNanos);
    }

    public record Line(String name, long count, long totalNanos, long maxNanos) {
        public String text() {
            return String.format(
                    Locale.ROOT,
                    "%s %d мс (n=%d, ср %.2f, макс %d)",
                    name,
                    totalNanos / NANOS_PER_MILLI,
                    count,
                    count == 0 ? 0.0 : totalNanos / (double) count / NANOS_PER_MILLI,
                    maxNanos / NANOS_PER_MILLI);
        }
    }

    public static List<Line> report() {
        List<Line> lines = new ArrayList<>();
        for (Map.Entry<String, Stat> entry : STATS.entrySet()) {
            Line line = entry.getValue().drain(entry.getKey());
            if (line.count() > 0) {
                lines.add(line);
            }
        }
        lines.sort(Comparator.comparingLong(Line::totalNanos).reversed().thenComparing(Line::name));
        return List.copyOf(lines);
    }

    private static final class Stat {
        private long count;
        private long total;
        private long max;

        synchronized void add(long nanos) {
            count++;
            total += nanos;
            max = Math.max(max, nanos);
        }

        synchronized Line drain(String name) {
            Line line = new Line(name, count, total, max);
            count = 0;
            total = 0;
            max = 0;
            return line;
        }
    }
}
