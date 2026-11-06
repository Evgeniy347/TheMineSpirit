package com.theminespirit.core.board;

import java.util.Optional;

public final class ClearSummary {
    public record Summary(boolean first, long boxes, long empty, long removed, long since) {}

    private final long window;
    private long since = -1;
    private long boxes;
    private long empty;
    private long removed;
    private boolean reportedFirst;

    public ClearSummary(long window) {
        if (window <= 0) {
            throw new IllegalArgumentException("Окно сводки " + window + " должно быть положительным: иначе сводка "
                    + "по [clear] снова заливает лог");
        }
        this.window = window;
    }

    public Optional<Summary> cleared(int removedBlocks, long now) {
        if (removedBlocks < 0) {
            throw new IllegalArgumentException("Убрано " + removedBlocks + " блоков: счёт не бывает отрицательным");
        }
        boxes++;
        removed += removedBlocks;
        return due(now);
    }

    public Optional<Summary> empty(long now) {
        boxes++;
        empty++;
        return due(now);
    }

    private Optional<Summary> due(long now) {
        if (since < 0) {
            since = now;
        }
        if (!reportedFirst) {
            reportedFirst = true;
            return Optional.of(new Summary(true, boxes, empty, removed, since));
        }
        if (now - since < window) {
            return Optional.empty();
        }
        Summary summary = new Summary(false, boxes, empty, removed, since);
        boxes = 0;
        empty = 0;
        removed = 0;
        since = now;
        reportedFirst = false;
        return Optional.of(summary);
    }
}
