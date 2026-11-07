package com.theminespirit.core.board;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record BoardTiming(long startedAt, long finishedAt) {
    public static final BoardTiming NONE = new BoardTiming(0, 0);
    public static final int JOURNAL_LOOKBACK = 1024;

    public BoardTiming {
        if (startedAt < 0 || finishedAt < 0) {
            throw new IllegalArgumentException("Тики времени задания " + startedAt + " и " + finishedAt
                    + " отрицательны: журнал идёт вперёд, а не назад");
        }
    }

    public boolean started() {
        return startedAt > 0;
    }

    public boolean finished() {
        return finishedAt > 0;
    }

    public long durationTicks(long now) {
        if (!started()) {
            return 0L;
        }
        long end = finished() ? finishedAt : Math.max(now, startedAt);
        return Math.max(0L, end - startedAt);
    }

    public static void require(long now) {
        if (now < 0) {
            throw new IllegalArgumentException("now равен " + now + " и отрицателен: время пересборки "
                    + "не может идти назад, иначе сортировка сломается");
        }
    }

    public static BoardTiming attempt(BoardStatus status, BoardTiming journal) {
        Objects.requireNonNull(status, "Статус не может быть null: по нему решают, идёт задание или закрыто");
        Objects.requireNonNull(journal, "Время журнала не может быть null: без него нечего уточнять");
        return switch (status) {
            case ASSIGNED -> new BoardTiming(journal.startedAt(), 0L);
            case DONE, FAILED, CANCELLED -> journal;
            default -> NONE;
        };
    }

    public static Map<String, BoardTiming> fromJournal(List<BoardLogEntry> entries) {
        Objects.requireNonNull(entries, "Журнал не может быть null: без него времени задания не узнать");
        Map<String, Long> starts = new LinkedHashMap<>();
        Map<String, Long> ends = new LinkedHashMap<>();
        for (BoardLogEntry entry : entries) {
            Objects.requireNonNull(entry, "Запись журнала не может быть null: по ней считают время задания");
            if (entry.event() == BoardLog.Event.ASSIGNED) {
                starts.merge(entry.ticketId(), entry.tick(), Math::max);
            } else if (closed(entry.event())) {
                ends.merge(entry.ticketId(), entry.tick(), Math::max);
            }
        }
        Map<String, BoardTiming> timings = new LinkedHashMap<>();
        for (Map.Entry<String, Long> start : starts.entrySet()) {
            timings.put(start.getKey(), timing(start.getValue(), ends.getOrDefault(start.getKey(), 0L)));
        }
        for (Map.Entry<String, Long> end : ends.entrySet()) {
            timings.putIfAbsent(end.getKey(), timing(0L, end.getValue()));
        }
        return Map.copyOf(timings);
    }

    private static BoardTiming timing(long startedAt, long finishedAt) {
        return new BoardTiming(startedAt, finishedAt >= startedAt ? finishedAt : 0L);
    }

    private static boolean closed(BoardLog.Event event) {
        return event == BoardLog.Event.DONE || event == BoardLog.Event.FAILED || event == BoardLog.Event.CANCELLED;
    }
}
